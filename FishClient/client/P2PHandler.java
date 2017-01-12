package fish.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import fish.common.MessageHandler;

public class P2PHandler implements Runnable
{
	private P2PHandler(Socket client, String path)
	{
		this.client = client;
		this.path = path;
	}

	@Override
	public void run() 
	{
		try 
		{
			BufferedOutputStream out = new BufferedOutputStream(client.getOutputStream());
			out.flush();
			ObjectInputStream in = new ObjectInputStream(client.getInputStream());	
			MessageHandler request = (MessageHandler) in.readObject();
						
			BufferedInputStream fileInput = 
					new BufferedInputStream(new FileInputStream((String) request.getContent()));

			
			byte[] buffer = new byte[1024];
			int len = 0;
			
			while((len = fileInput.read(buffer)) > 0)
				out.write(buffer, 0, len);
			
			fileInput.close();
			out.close();
			in.close();
						
		} catch (IOException | ClassNotFoundException e) 
		{
			e.printStackTrace();
		}
	}
	
	public static void start(int port, String path)
	{
		Runnable p2pHandler = () -> 
		{
			System.out.println(port);
			
			try(ServerSocket socket = new ServerSocket(port))
			{
				System.out.println("Starting P2PHandler...");
			
				while(true)
				{
					Socket client = socket.accept();
					new Thread(new P2PHandler(client, path)).start();
				}
			} 
			catch (IOException e) 
			{
				System.out.println("P2PHandler has crashed...");
				e.printStackTrace();
			}
		};
		
		new Thread(p2pHandler).start();
	}
	private Socket client;
	private String path;
}