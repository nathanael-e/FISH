package fish.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import fish.common.FishFile;
import fish.common.Message;
import fish.common.MessageHandler;

public class ClientHandler implements Runnable 
{
	final static String UPDATE_VIOLATION = "An update request must contain an Arraylist of strings as content";
	final static String SEARCH_VIOLATION = "A search request must contain a String as content";
	
	public ClientHandler(Server server, Socket client)
	{
		this.server = server;
		this.client = client;
	}
	
	private boolean connect()
	{
		try 
		{
			out = new ObjectOutputStream(client.getOutputStream());
			in = new ObjectInputStream(client.getInputStream());
			out.flush();
		} catch (IOException e) 
		{
			System.out.println("Failed to connect to client");
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean register()
	{
		try 
		{   				
			MessageHandler msg = (MessageHandler) in.readObject();
			
			if(msg.getMessage() != Message.REGISTER)
				return false;

			ArrayList<String> filesToShare = (ArrayList<String>) msg.getContent(); 
			server.put(client, filesToShare);
			out.writeObject(new MessageHandler(Message.REGISTER_ACK, null));
		} catch (ClassNotFoundException | IOException e) {
			System.out.println("Failed to fetch filelist from client");
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private MessageHandler update(Object o)
	{	
		if(!isArrayListOfStrings(o))
			return new MessageHandler(Message.PROTOCOL_VIOLATION, UPDATE_VIOLATION);
		
		@SuppressWarnings("unchecked")
		ArrayList<String> filenames = (ArrayList<String>) o;
		
		if(server.removeAll(client.getInetAddress().getHostAddress()) && 
		   server.put(client, filenames))
			return new MessageHandler(Message.UPDATE_ACK, null);
		else
			return new MessageHandler(Message.ERROR, "Update failed. Try again");
	}
	
	private MessageHandler search(Object o)
	{
		if(!(o instanceof String))
			return new MessageHandler(Message.PROTOCOL_VIOLATION, SEARCH_VIOLATION);
		
		ArrayList<FishFile> files = server.search((String) o);
		
		if(files != null)
			return new MessageHandler(Message.SEARCH_ACK, files);
		else
			return new MessageHandler(Message.ERROR, "Failed to fetch serach results");
	}
	
	private boolean isArrayListOfStrings(Object o)
	{		
		ListOfStrings l = (ArrayList<?> list) ->
		{
			for(Object object:list)
				if(!(object instanceof String))
					return false;
			return true;
		};
		
		return (o instanceof ArrayList<?>) ? l.checkStrings((ArrayList<?>) o) : false;			
	}
	
	private void listen()
	{	
		while(true)
		{
			try 
			{
				MessageHandler msg = (MessageHandler) in.readObject();
				
				switch(msg.getMessage())
				{
					case UPDATE:
						out.writeObject(update(msg.getContent()));
						break;
					
					case SEARCH:
						out.writeObject(search(msg.getContent()));
						break;
						
					default:
						System.out.println("Invalid arguments");
				}
				
			} catch (ClassNotFoundException e) 
			{
				e.printStackTrace();
			} catch (IOException e) 
			{
				System.out.println("Connection lost...");
				server.removeAll(client.getInetAddress().getHostAddress());
				e.printStackTrace();
				break;
			}
		}
	}
	
	public void run() 
	{		
		if(connect() && register()) 
			listen();
	}
	
	interface ListOfStrings{boolean checkStrings(ArrayList<?> list);}
	
	private Socket client;
	private Server server;
	private ObjectInputStream in;
	private ObjectOutputStream out;
}