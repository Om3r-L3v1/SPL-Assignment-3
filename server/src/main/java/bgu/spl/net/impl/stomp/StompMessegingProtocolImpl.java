package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessegingProtocolImpl<T> implements StompMessagingProtocol<T> {
    private Integer connectionId;
    private boolean shouldTerminate;
    private Connections<T> connections;
    private Map<String, String> activeSubscriptions = new HashMap<>();
    private String currentUser;
    private static final ConcurrentHashMap<String, Integer> loggedInUsers = new ConcurrentHashMap<>();
    
    private static final AtomicInteger messageIdCounter = new AtomicInteger(0);
    @Override
    public void start(int connectionId, Connections<T> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public T process(T message) {
        try {
            StompFrame frame = new StompFrame((String)message);
            
            if (connections == null) {
                throw new IllegalStateException("Protocol not started");
            }

            boolean success = false;

            switch (frame.getCommand()) {
                case "CONNECT":
                    handleConnect(frame);
                    success = !shouldTerminate;
                    break;
                case "SEND":
                    validateLogin();
                    handleSend(frame);
                    success = !shouldTerminate; 
                    break;
                case "SUBSCRIBE":
                    validateLogin();
                    handleSubscribe(frame);
                    success = !shouldTerminate; 
                    break;
                case "UNSUBSCRIBE":
                    validateLogin();
                    handleUnsubscribe(frame);
                    success = !shouldTerminate; 
                    break;
                case "DISCONNECT":
                    validateLogin();
                    handleDisconnect(frame);
                    success = true; 
                    shouldTerminate = true;
                    break;
                default:
                    handleError("Unknown command: " + frame.getCommand());
                    break;
            }

            if (success && frame.containsHeader("receipt")) {
                String receiptId = frame.getHeader("receipt");
                String receiptFrame = "RECEIPT\nreceipt-id:" + receiptId + "\n\n\u0000";
                connections.send(connectionId, (T)receiptFrame);
                
                if (frame.getCommand().equals("DISCONNECT")) {
                    connections.disconnect(connectionId);
                }
            }
            return null;

        } catch (Exception e) {
            handleError("Exception processed: " + e.getMessage());
            return null;
        }
    }

    private void handleConnect(StompFrame frame) {
        String version = frame.getHeader("accept-version");
        String login = frame.getHeader("login");
        String passcode = frame.getHeader("passcode");
        String host = frame.getHeader("host");

        if (version == null || login == null || passcode == null || host == null) {
            handleError("Missing required headers in CONNECT frame");
            return;
        }

        if (!version.equals("1.2")) {
            handleError("Unsupported STOMP version: " + version);
            return;
        }

        if (!host.equals("stomp.cs.bgu.ac.il")) {
             handleError("Invalid host: " + host);
             return;
        }
        if (authenticateUser(login, passcode)) {
            this.currentUser = login;            
            String connectedFrame = "CONNECTED\nversion:1.2\n\n\u0000";
            connections.send(connectionId, (T)connectedFrame);
        }
    }
    private boolean authenticateUser(String username, String password) {
        LoginStatus status = Database.getInstance().login(connectionId, username, password);
        switch (status) {
            case LOGGED_IN_SUCCESSFULLY:
            case ADDED_NEW_USER:
                return true;

            case ALREADY_LOGGED_IN:
            case CLIENT_ALREADY_CONNECTED: 
                handleError("User already logged in");
                return false;

            case WRONG_PASSWORD:
                handleError("Wrong password");
                return false;

            default:
                handleError("Authentication failed");
                return false;
        }
    }

    private void handleDisconnect(StompFrame frame) {
        
        if (currentUser != null) {
            loggedInUsers.remove(currentUser);
            Database.getInstance().logout(connectionId);
        if(!frame.containsHeader("receipt")){
            handleError("Missing receipt header in DISCONNECT frame");
            return;
        }
        shouldTerminate = true;
        }
        handleError("user not connected");

    }

    private void handleSubscribe(StompFrame frame) {
        String destination = frame.getHeader("destination");
        String id = frame.getHeader("id");

        if (destination == null || id == null) {
            handleError("Missing required headers in SUBSCRIBE frame");
            return;
        }

        activeSubscriptions.put(destination, id);
        ConnectionImpl<T> connImpl = (ConnectionImpl<T>) connections;
        connImpl.subscribe(destination, connectionId, id);
    }

    private void handleUnsubscribe(StompFrame frame) {
        String id = frame.getHeader("id");
        if (id == null) {
            handleError("Missing id header in UNSUBSCRIBE frame");
            return;
        }

        String topicToRemove = null;
        for (Map.Entry<String, String> entry : activeSubscriptions.entrySet()) {
            if (entry.getValue().equals(id)) {
                topicToRemove = entry.getKey();
                break;
            }
        }

        if (topicToRemove != null) {
            activeSubscriptions.remove(topicToRemove);
            ConnectionImpl<T> connImpl = (ConnectionImpl<T>) connections;
            connImpl.unsubscribe(topicToRemove, connectionId);
        }
    }

    private void handleSend(StompFrame frame) {
        String destination = frame.getHeader("destination");
        String body = frame.getBody();
        String filename = frame.getHeader("file");

        if (destination == null) {
            handleError("Missing destination header in SEND frame");
            return;
        }

        if (!activeSubscriptions.containsKey(destination)) {
            handleError("User not subscribed to: " + destination);
            return;
        }
        if(filename != null){
            Database.getInstance().trackFileUpload(currentUser, filename, destination);
        }

        String messageFrame = "MESSAGE\n" +
                              "destination:" + destination + "\n" +
                              "message-id:" + messageIdCounter.incrementAndGet() + "\n" +
                              "\n" + 
                              body + "\u0000";

        connections.send(destination, (T)messageFrame);
    }


    private void validateLogin() {
        if (currentUser == null) {
            throw new IllegalStateException("User not connected"); 
        }
    }

    private void handleError(String message) {
        String errorFrame = "ERROR\nmessage:" + message + "\n\n" + 
                            "The connection will be closed.\u0000";
        connections.send(connectionId, (T)errorFrame);
        
        if (currentUser != null) {
            loggedInUsers.remove(currentUser);
            Database.getInstance().logout(connectionId);
        }
        
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }


    private static class StompFrame {

    private String command;
    private final Map<String, String> headers = new HashMap<>();
    private String body;

    private StompFrame(String message) {
        parse(message);
    }

    private void parse(String message) {
        String[] parts = message.split("\n\n", 2);

        String headerSection = parts[0];

        if (parts.length > 1) {
            this.body = parts[1];
        } else {
            this.body = "";
        }

        String[] lines = headerSection.split("\n");

        if (lines.length > 0) {
            this.command = lines[0].trim();
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] kv = line.split(":", 2);

            if (kv.length == 2) {
                headers.put(kv[0].trim(), kv[1].trim());
            }
        }
    }

    public String getCommand() {
        return command;
    }

    public String getHeader(String key) {
        return headers.get(key);
    }
    
    public boolean containsHeader(String key) {
        return headers.containsKey(key);
    }

    public String getBody() {
        return body;
    }
}
}


