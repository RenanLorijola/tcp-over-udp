import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Receiver {

    public static final Integer PACOTE_LENTO_SEGUNDOS = 6;

    public static Integer ultimoIdRecebidoCorretamente = -1;

    public static DatagramSocket socket;

    public static void main(String[] args) {
        try {
            socket = new DatagramSocket(9876);

            while(true){
                byte[] recBuffer = new byte[1024];
                DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
                socket.receive(recPkt);

                String mensagemRecebidaJSON = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
                Gson gson = new Gson();
                Mensagem mensagemRecebida = gson.fromJson(mensagemRecebidaJSON, Mensagem.class);
                Integer tipo = mensagemRecebida.getTipo();

                //se for a mensagem com o id esperado, o ack é incrementado para a resposta
                if(mensagemRecebida.getId() == ultimoIdRecebidoCorretamente + 1){
                    ultimoIdRecebidoCorretamente++;
                    System.out.println("Mensagem id " + mensagemRecebida.getId() + " recebida na ordem, entregando para a camada de aplicação.");
                //se for uma mensagem fora de ordem, peça novamente o ack do pacote não recebido
                }else if(mensagemRecebida.getId() > ultimoIdRecebidoCorretamente + 1){
                    System.out.print("Mensagem id " + mensagemRecebida.getId() + " fora de ordem, ainda não recebidos os identificadores [");
                    for(int i = ultimoIdRecebidoCorretamente + 1; i < mensagemRecebida.getId(); i++){
                        System.out.print(i);
                        if(i != mensagemRecebida.getId()-1){
                            System.out.print(", ");
                        }
                    }
                    System.out.println("]");
                //se for uma mensagem duplicada, ignore o pacote e envie novamente seu ack
                }else {
                    System.out.println("Mensagem id " + mensagemRecebida.getId() + " recebida de forma duplicada.");
                }

                //envia a resposta simulando lentidão dependendo do tipo da mensagem
                ReceiverEnviarRespostaThread thread = new ReceiverEnviarRespostaThread(recPkt, tipo);
                thread.start();
            }
        } catch (Exception e){
            System.out.println("Um erro inesperado ocorreu");
        }
    }

    public static class ReceiverEnviarRespostaThread extends Thread {

        private DatagramPacket recPkt;
        private Integer tipo;
        private Gson gson;

        public ReceiverEnviarRespostaThread(DatagramPacket recPkt, Integer tipo) {
            this.recPkt = recPkt;
            this.tipo = tipo;
            gson = new Gson();
        }

        @Override
        public void run(){
            try {
                Mensagem mensagemResposta = new Mensagem(ultimoIdRecebidoCorretamente);
                String responseString = gson.toJson(mensagemResposta);

                byte[] sendBuffer;
                sendBuffer = responseString.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, recPkt.getAddress(), recPkt.getPort());

                //se for um pacote lento, demore mais para enviar o pacote
                if(tipo == 1){
                    this.sleep(1000 * PACOTE_LENTO_SEGUNDOS);
                }
                socket.send(sendPacket);
            } catch (Exception e){
                System.out.println("Um erro inesperado ocorreu ao devolver o pacote de resposta");
            }
        }
    }
}
