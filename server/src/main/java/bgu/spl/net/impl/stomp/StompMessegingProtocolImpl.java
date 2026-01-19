package bgu.spl.net.impl.stomp;

import java.util.HashMap;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessegingProtocolImpl<T> implements StompMessagingProtocol<T> {
    private Integer connectionId;
    private boolean shouldTerminate;
    private Connections<String> connection;
    private String currentUser = null;

    @Override
    public void start(int connectionId, Connections connections) {
        this.connectionId = connectionId;
        this.connection = connections;
    }

    @Override
    public void process(T message) {
        StompFrame frame = new StompFrame((String)message);
        switch (frame.command) {
            case "MESSAGE":
                
                break;
        
            default:
                break;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }


    private class StompFrame{
            private String rawMsg;
            private String command;
            private HashMap<String,String> headers;
            private String body;
            public StompFrame(String message){
                headers = new HashMap<String,String>();
                parse(message);
            }
            public void parse(String message){
                String[] parts = message.split("\n\n");
                if(parts.length>2){
                    throw new IllegalArgumentException("msg is misformated");
                }
                String headersAndCommand = parts[0];
                this.body = parts[1];
                String[] lines = headersAndCommand.split("\n");
                this.command = lines[0];
                for(int i = 1;i<lines.length;i++){
                    String[] kv = lines[i].split(":");
                    if(kv.length != 2){
                        throw new IllegalArgumentException("msg is misformated");
                    }
                    this.headers.put(kv[0], kv[1]);
                }
                this.rawMsg = message;
            }
            
    }
}

