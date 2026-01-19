package bgu.spl.net.impl.stomp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import bgu.spl.net.srv.*;;
public class ConnectionImpl<T> implements Connections<T>{

    private ConcurrentHashMap<Integer,ConnectionHandler<T>> connectionHandlersById;
    private ConcurrentHashMap<String, List<Integer>> connectionHandlersByStream;
    public ConnectionImpl (ConcurrentHashMap<Integer,ConnectionHandler<T>> connHashMapId,ConcurrentHashMap<String, List<Integer>> connHashMapByStream){
        this.connectionHandlersById = connHashMapId;
        this.connectionHandlersByStream = connHashMapByStream;   
    }
    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> clientConn = connectionHandlersById.get(connectionId);
        if(clientConn == null){
            return false;
        }
        try{
            clientConn.send(msg);
            return true;
        }catch(Exception e){
            System.err.println("Failed to send message to ID " + connectionId + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void send(String channel, T msg) {
        List<Integer> subscribers = connectionHandlersByStream.get(channel);
        for (Integer connectionId : subscribers) {
            send(connectionId, msg);
        }
    }

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<T> clientConn = connectionHandlersById.get(connectionId);
        if(clientConn == null){
            throw new NullPointerException("clientConnection exist as null");
        }        
        try{
            clientConn.close();
        }
        catch(Exception e){
            System.err.println(e.getMessage());
        }
        connectionHandlersById.remove(connectionId);
        for (List<Integer> subscribers : connectionHandlersByStream.values()) {
            subscribers.remove(connectionId);
        }

    }
    
}
