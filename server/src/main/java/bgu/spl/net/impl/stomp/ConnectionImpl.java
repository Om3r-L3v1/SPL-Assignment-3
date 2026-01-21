package bgu.spl.net.impl.stomp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import bgu.spl.net.srv.*;;
public class ConnectionImpl<T> implements Connections<T>{

    private ConcurrentHashMap<Integer,ConnectionHandler<String>> connectionHandlersById;
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channelSubscribers;
    public ConnectionImpl() {
        this.connectionHandlersById = new ConcurrentHashMap<>();
        this.channelSubscribers = new ConcurrentHashMap<>();
    }   
    public ConnectionImpl (ConcurrentHashMap<Integer,ConnectionHandler<String>> connHashMapId,ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channelSubscribers){
        this.connectionHandlersById = connHashMapId;
        this.channelSubscribers = channelSubscribers;
    }
    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<String> clientConn = connectionHandlersById.get(connectionId);
        if(clientConn == null){
            return false;
        }
        try{
            clientConn.send((String)msg);
            return true;
        }catch(Exception e){
            throw new RuntimeException("Failed to send message to ID " + connectionId + ": " + e.getMessage());
        }
    }

    @Override
    public void send(String channel, T msg) {
        ConcurrentHashMap<Integer, String> subscribers = channelSubscribers.get(channel);
        if (subscribers != null) {
            for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {
                Integer connId = entry.getKey();
                String subId = entry.getValue();
                String msgWithSubId = addSubscriptionHeader((String)msg, subId);
                send(connId, (T)msgWithSubId);
            }
        }
    }

    private String addSubscriptionHeader(String msg, String subId) {
            String[] parts = msg.split("\n", 2);
            if (parts.length < 2)
                return msg + "\nsubscription:" + subId;
            return parts[0] + "\nsubscription:" + subId + "\n" + parts[1];
        }
    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<String> clientConn = connectionHandlersById.get(connectionId);
        if(clientConn == null){
            throw new NullPointerException("clientConnection exist as null");
        }        
        try{
            clientConn.close();
        }
        catch(Exception e){
            throw new RuntimeException(e.getMessage());
        }
        connectionHandlersById.remove(connectionId);
        for (Map<Integer, String> subscribers : channelSubscribers.values()) {
            subscribers.remove(connectionId);
        }
    }
    public void addConnection(int connectionId, ConnectionHandler<String> handler) {
        connectionHandlersById.put(connectionId, handler);
    }

    public void subscribe(String channel, int connectionId, String subscriptionId) {
        channelSubscribers.putIfAbsent(channel, new ConcurrentHashMap<>());
        channelSubscribers.get(channel).put(connectionId, subscriptionId);
    }

    public void unsubscribe(String channel, int connectionId) {
        if (channel == null) {
            return;
        }
        Map<Integer, String> subscribers = channelSubscribers.get(channel);
        if (subscribers != null) {
            subscribers.remove(connectionId);
        }
    }
    
}
