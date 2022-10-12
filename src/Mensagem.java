public class Mensagem {
    private String data;
    private Integer id;
    private Integer ack;
    private Integer tipo;

    public Mensagem(String message, Integer id, Integer tipo){
        this.data = message;
        this.id = id;
        this.tipo = tipo;
    }

    public Mensagem(int ack){
        this.ack = ack;
    }

    public String getData() {
        return data;
    }

    public int getId() {
        return id;
    }

    public int getAck() {
        return ack;
    }

    public Integer getTipo() {
        return tipo;
    }
}
