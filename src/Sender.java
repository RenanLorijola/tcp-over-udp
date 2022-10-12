import com.google.gson.Gson;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Sender {

    public static final Integer TEMPORIZADOR_SEGUNDOS = 4;
    public static final Integer JANELA_TAMANHO = 5;
    public static final String[] opcoes = new String[]{"normal", "lenta", "perda", "fora de ordem", "duplicada"};

    public static DatagramSocket socket;
    public static InetAddress ipAddress;
    public static final Integer serverPort = 9876;

    public static ArrayList<Integer> idsEnviados = new ArrayList<>();
    public static DatagramPacket[] bufferPacotes = new DatagramPacket[9999];

    public static ArrayList<DatagramPacket> pacotesForaDeOrdem = new ArrayList<>();

    public static Integer base = 0;
    public static Integer nextseqnum = 0;

    public static Boolean threadExecutando = false;

    public static void main(String[] args) throws SocketException, UnknownHostException {
        socket = new DatagramSocket();
        ipAddress = InetAddress.getByName("127.0.0.1");

        Scanner scanner = new Scanner(System.in);
        String mensagemMenu = "----------------------------------------\nEscolha o tipo de mensagem a ser enviada:\n0 - Normal\n1 - Lenta\n2 - Perda\n3 - Fora de Ordem\n4 - Duplicada\n----------------------------------------";

        // cria uma thread que irá receber todos os acks e atualizar o valor "base", servira como "listener" das respostas do servidor
        SenderReceberAckThread senderReceberAckThread = new SenderReceberAckThread();
        senderReceberAckThread.start();

        Gson gson = new Gson();

        while (true) {
            try {
                System.out.println(mensagemMenu);
                //captura o tipo e valida se o tipo é entre 0 e 4
                Integer tipo = Integer.parseInt(scanner.nextLine());
                while (tipo < 0 || tipo > 4) {
                    System.out.println("Tipo invalido, tente novamente\n");
                    System.out.println(mensagemMenu);
                    tipo = Integer.parseInt(scanner.nextLine());
                }

                //captura a mensagem do pacote
                System.out.println("Digite a mensagem a ser enviada");
                String inputMensagem = scanner.nextLine();

                //valida se o pacote esta fora da janela e é um pacote não autorizado
                if(base + JANELA_TAMANHO <= nextseqnum){
                    System.out.println("Pacote fora da janela, não autorizado!");
                } else {
                    //envia a mensagem para o servidor
                    Mensagem mensagem = new Mensagem(inputMensagem, nextseqnum, tipo);
                    String responseString = gson.toJson(mensagem);
                    byte[] sendBuffer;
                    sendBuffer = responseString.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, ipAddress, serverPort);

                    System.out.println("Mensagem \"" + mensagem.getData() + "\" enviada como [" + opcoes[mensagem.getTipo()] + "] com id " + mensagem.getId());

                    //se não for um pacote fora de ordem ou perdido envie o mesmo. Se for fora de ordem, armazene até enviar o proximo pacote
                    if(tipo != 3 && tipo != 2){
                        helperEnviaPacoteAdicionaNosIdsEnviados(sendPacket);
                    }
                    //se for duplicado, reenvie o pacote
                    if(tipo == 4){
                        socket.send(sendPacket);
                    }
                    //se o pacote for perdido, ele não vai ser enviado pelo socket (só por questões de simulação), mas sera colocado no array de enviados, como se ele tivesse sido
                    if(tipo == 2){
                        idsEnviados.add(mensagem.getId());
                    }

                    //se houver pacotes fora de ordem para serem enviados, e o pacote atual também não for fora de ordem, envie todos e limpe o array de pacotes fora de ordem
                    if(!pacotesForaDeOrdem.isEmpty() && tipo != 3){
                        for (DatagramPacket pacoteForaDeOrdem : pacotesForaDeOrdem){
                            helperEnviaPacoteAdicionaNosIdsEnviados(pacoteForaDeOrdem);
                        }
                        pacotesForaDeOrdem.removeAll(pacotesForaDeOrdem);
                    }

                    //se for um pacote fora de ordem, armazene e espere o proximo envio de um pacote para ser enviado
                    if(tipo == 3){
                        pacotesForaDeOrdem.add(sendPacket);
                    }

                    //armazena o pacote no array de pacotes enviados, caso precise de reenvio, e incrementa o nextseqnum para o proximo pacote
                    bufferPacotes[mensagem.getId()] = sendPacket;
                    nextseqnum++;
                }
            } catch (Exception e){
                System.out.println("Um erro inesperado ocorreu");
            }
        }
    }

    private static void helperEnviaPacoteAdicionaNosIdsEnviados(DatagramPacket packet) throws IOException {
        Gson gson = new Gson();
        String mensagemJSON = new String(packet.getData(), packet.getOffset(), packet.getLength());
        Mensagem mensagem = gson.fromJson(mensagemJSON, Mensagem.class);

        socket.send(packet);
        idsEnviados.add(mensagem.getId());

        //inicia a thread que vai cuidar do reenvio de pacotes e temporizador
        if(!threadExecutando){
            SenderEnviarPacoteThread senderEnviarPacoteThread = new SenderEnviarPacoteThread();
            senderEnviarPacoteThread.start();
        }
    }

    public static class SenderReceberAckThread extends Thread {
        private Gson gson;

        public SenderReceberAckThread(){
            this.gson =  new Gson();
        }

        @Override
        public void run(){
            try {
                while (true){
                    byte[] recBuffer = new byte[1024];
                    DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
                    socket.receive(recPkt); //aguarda receber uma resposta do servidor

                    String mensagemRecebidaJSON = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
                    Mensagem mensagemRecebida = gson.fromJson(mensagemRecebidaJSON, Mensagem.class);

                    Integer ackRecebido = mensagemRecebida.getAck();

                    // se receber uma resposta de ack maior ou igual, iguala a base ao ack recebido + 1, pois a base é o proximo valor não recebido
                    if(ackRecebido >= base){
                        System.out.println("Mensagem id " + ackRecebido + " recebida pelo receiver");
                        base = ackRecebido + 1;
                    }
                }
            } catch (Exception e) {
                System.out.println("Um erro inesperado ocorreu ao esperar a resposta do servidor");
            }
        }
    }

    public static class SenderEnviarPacoteThread extends Thread {
        private Gson gson;
        public SenderEnviarPacoteThread(){
            this.gson =  new Gson();
        }

        @Override
        public void run() {
            try {
                threadExecutando = true;
                //verifica se há pacotes para serem enviados
                while(base < nextseqnum){
                    //espera o timeout acontecer
                    this.sleep(1000 * TEMPORIZADOR_SEGUNDOS);
                    //verifica se pelo menos um id já foi enviado (para evitar enviar pacotes fora de ordem)
                    if (idsEnviados.size() > 0) {
                        //encontra qual o ultimo id de pacote enviado
                        int idUltimoPacoteEnviado = Collections.max(idsEnviados);

                        //verifica se o ultimo pacote enviado é um pacote que está sendo esperado o ack
                        if (idUltimoPacoteEnviado >= base) {
                            for (int i = base; i <= idUltimoPacoteEnviado; i++) {
                                //verifica se o pacote daquela posição ja foi enviado (evita tentar acessar o valor de um pacote fora de ordem que ainda está esperando um outro pacote ser enviado)
                                if (bufferPacotes[i] != null) {
                                    socket.send(bufferPacotes[i]);
                                }
                            }
                        }
                    }
                }
                threadExecutando = false;
            } catch (Exception e) {
                System.out.println("Um erro inesperado ocorreu ao esperar a resposta do servidor");
            }
        }
    }
}
