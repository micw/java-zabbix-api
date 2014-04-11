package org.github.zabbix.sender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.json.JSONObject;

/**
 * See:
 * - https://www.zabbix.com/documentation/2.0/manual/appendix/items/activepassive
 * - https://www.zabbix.org/wiki/Docs/protocols/zabbix_sender/1.8/java_example
 * @author mwyraz
 */
public class ZabbixSender
{
    public static final int DEFAULT_PORT=10051;
    public static final byte[] ZABBIX_HEADER=new byte[] {'Z', 'B', 'X', 'D','\1',};

    public ZabbixSender()
    {
    }
    public ZabbixSender(String host)
    {
        this(host,DEFAULT_PORT);
    }
    public ZabbixSender(String zabbixServer, int port)
    {
        this.zabbixServer=zabbixServer;
        this.port=port;
    }
    
    protected String zabbixServer;
    protected int port=DEFAULT_PORT;
    
    public void setZabbixServer(String zabbixServer)
    {
        this.zabbixServer = zabbixServer;
    }
    public void setPort(int port)
    {
        this.port = port;
    }
    
    public ZabbixSenderResponse sendItems(ZabbixSenderItem... items) throws IOException
    {
        JSONObject requestObj=new JSONObject();
        requestObj.put("request", "sender data");
        requestObj.put("data",items);
        return send(requestObj.toString());
    }
    
    protected ZabbixSenderResponse send(String jsonMessage) throws IOException
    {
        byte[] messageBytes=jsonMessage.getBytes("utf-8");
        int v=messageBytes.length;
        
        Socket sock=new Socket();
        sock.setSoTimeout(30000);
        sock.connect(new InetSocketAddress(zabbixServer, port), 30000);
        try
        {
            OutputStream out=sock.getOutputStream();
            InputStream in=sock.getInputStream();
            
            out.write(ZABBIX_HEADER);
            
            out.write((byte)(v >>  0) & 0xff);
            out.write((byte)(v >>  8) & 0xff);
            out.write((byte)(v >> 16) & 0xff);
            out.write((byte)(v >> 24) & 0xff);
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(0);
            
            out.write(messageBytes);
            out.flush();
            
            for (byte headerByte: ZABBIX_HEADER)
            {
                if (in.read()!=headerByte) throw new IOException("Received invalid zabbix-header");
            }
            byte[] readBuffer=new byte[8];
            if ((in.read(readBuffer))!=8) throw new IOException("Received invalid zabbix-header");

            long messageLength=(((long)readBuffer[7] << 56) +
                ((long)(readBuffer[6] & 255) << 48) +
                ((long)(readBuffer[5] & 255) << 40) +
                ((long)(readBuffer[4] & 255) << 32) +
                ((long)(readBuffer[3] & 255) << 24) +
                ((readBuffer[2] & 255) << 16) +
                ((readBuffer[1] & 255) <<  8) +
                ((readBuffer[0] & 255) <<  0));
            
            if (messageLength<0 || messageLength>65535) throw new IOException("Received invalid zabbix-header (message length: "+messageLength+")");
            
            byte[] message=new byte[(int)messageLength];
            if ((in.read(message))!=messageLength) throw new IOException("Received invalid zabbix message (message too short)");
            
            
            
            JSONObject responseObj=new JSONObject(new String(message,"utf-8"));
            
            return new ZabbixSenderResponse(responseObj.getString("response"),responseObj.getString("info"));
        }
        finally
        {
            sock.close();
        }
    }
    
    public static class ZabbixSenderResponse
    {
        public ZabbixSenderResponse()
        {
        }
        public ZabbixSenderResponse(String response, String info)
        {
            this.response = response;
            this.info = info;
        }
        public String response;
        public String info;
        
        @Override
        public String toString()
        {
            return response+": "+info;
        }
        
    }
    public static class ZabbixSenderItem
    {
        public ZabbixSenderItem()
        {
        }
        public ZabbixSenderItem(String host, String key, String value)
        {
            this.host = host;
            this.key = key;
            this.value = value;
        }


        protected String host;
        protected String key;
        protected String value;
        
        public String getHost()
        {
            return host;
        }
        public void setHost(String host)
        {
            this.host = host;
        }
        public String getKey()
        {
            return key;
        }
        public void setKey(String key)
        {
            this.key = key;
        }
        public String getValue()
        {
            return value;
        }
        public void setValue(String value)
        {
            this.value = value;
        }
        
        
    }
    
}

