package bgu.spl.net.impl.stomp;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.srv.Server;
public class StompServer {

    public static void main(String[] args) {
        int port = 8686;
        String serverType = "tpc"; 
        if (args.length >= 2) {
            port = Integer.parseInt(args[0]);
            serverType = args[1];
        }
        Server<String> server;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nServer is shutting down... generating report:");
            Database.getInstance().printReport();
        }));
        if (serverType.equals("tpc")) {
            Server.threadPerClient(
                    port,
                    () -> new StompMessegingProtocolImpl<String>(), // שים לב ל-e בשם המחלקה ול-<String>
                    () -> new MessageEncoderDecoderImp()           // השם הנכון של ה-Encoder שלך
            ).serve();

        } else if (serverType.equals("reactor")) {
            Server.reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    () -> new StompMessegingProtocolImpl<String>(),
                    () -> new MessageEncoderDecoderImp()
            ).serve();
        } else {
            System.out.println("Unknown server type: " + serverType);
        }
    }
    
}