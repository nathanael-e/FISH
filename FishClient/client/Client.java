package fish.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

import fish.common.FishFile;
import fish.common.Message;
import fish.common.MessageHandler;

public class Client implements Runnable
{
	static final String DEFAULT_IP = "130.229.182.250";
	static final int DEFAULT_PORT_NUMBER = 6123;
	static final String DEFAULT_DOWNLOAD_PATH = "downloads";
	static final String DEFUALT_SHARED_PATH = "shared";
	
	public Client()
	{
		this(DEFAULT_IP, DEFAULT_PORT_NUMBER, DEFAULT_DOWNLOAD_PATH, DEFUALT_SHARED_PATH);
	}
	
	public Client(String ip, int portNumber, String downloadPath, String sharedPath)
	{
		this.ip = ip;
		this.portNumber = portNumber;
		this.downloadPath = downloadPath;
		this.sharedPath = sharedPath;
	}
	
	public void run()
	{
		try(Socket serverSocket = new Socket(ip, portNumber))
		{			
			if(connect(serverSocket) && register())
			{
				P2PHandler.start(serverSocket.getLocalPort(), downloadPath);
				console();
			}

		} catch (UnknownHostException e) 
		{
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void console() 
	{
		inFromClient = new Scanner(System.in);
		
		println("Welcome to FISH file sharing!");
		
		console:while(true)
		{
			print("\n1. Serach \n2. Update\n3. Exit\n");
			String input = inFromClient.nextLine();
			
			switch(input)
			{
				case "1":
					try {
						search();
					} catch (ClassNotFoundException | IOException e) {
						e.printStackTrace();
					}
		
					break;
				
				case "2":
					try {
						update();
					} catch (ClassNotFoundException | IOException e) {
						error("Update failed");
						e.printStackTrace();
					}
					
					break;
				case "3":
					try {
						exit();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					break console;
				default:
					println("Invalid input.");
			}
		}
		
		inFromClient.close();
	}
	
	private boolean connect(Socket serverSocket)
	{
		try 
		{
			out = new ObjectOutputStream(serverSocket.getOutputStream());
			in = new ObjectInputStream(serverSocket.getInputStream());
			out.flush();

		} catch (IOException e) 
		{
			println("Failed to establish connection");
			e.printStackTrace();
			return false;
		}
		
		println("Connected...");
		return true;
	}
	
	private boolean register()
	{
		try 
		{
			out.writeObject(new MessageHandler(Message.REGISTER, fetchLocalFiles()));
			MessageHandler msg = (MessageHandler) in.readObject();
			
			if(msg.getMessage() != Message.REGISTER_ACK)
			{
				error("Protocol violation: did "
						+ "not receive registration acknowledgment.");
				return false;
			}
			
		} catch (IOException  | ClassNotFoundException e) 
		{
			error("Server communication failed.");
			e.printStackTrace();
			return false;
		}

		println("Registered with server...");
		return true;
	}
	
	private void search() throws IOException, ClassNotFoundException
	{
		println("Fish serach: ");
		String keyword = inFromClient.nextLine();
		out.writeObject(new MessageHandler(Message.SEARCH, keyword));
		MessageHandler msg = (MessageHandler) in.readObject();
		
		if(msg.getMessage() != Message.SEARCH_ACK)
		{
			error("Protocol violation: did not receive search ack.");
			return;
		}
		
		@SuppressWarnings("unchecked")
		ArrayList<FishFile> searchResults = (ArrayList<FishFile>) msg.getContent();	
		
		if(searchResults.isEmpty())
			System.out.println("No match for \"" + keyword + "\".");
		else
		{
			for(int index = 0; index < searchResults.size(); index++)
				System.out.println((index + 1) + ": " + searchResults.get(index).getFilename());
			
			String input = inFromClient.nextLine();
			
			if(input.matches("^-?\\d+$"))
			{
				int i = Integer.parseInt(input) - 1;
				
				if((i >= 0) && i < searchResults.size())
					fetch(searchResults.get(i));
			}   
		}
	}
	
	private void fetch(FishFile peer)
	{
		System.out.println("IP: " + peer.getIp() + " PortNumber: " + peer.getPortNumber());
		
		try(Socket remote = new Socket(peer.getIp(), peer.getPortNumber()))
		{
			ObjectOutputStream outToPeer = new ObjectOutputStream(remote.getOutputStream());
			BufferedInputStream inToClient = new BufferedInputStream(remote.getInputStream());
			BufferedOutputStream toDisk = new BufferedOutputStream(
							new FileOutputStream(downloadPath + File.separator +  peer.getFilename()));
		
			outToPeer.writeObject(new MessageHandler(Message.FETCH, peer.getPath()));
			
			byte[] buffer = new byte[1024];
			int len = 0;
			
			println("Downloading file: " + peer.getFilename());
			
			while((len = inToClient.read(buffer)) > 0)
				toDisk.write(buffer, 0, len);
			
			println("Finished downloading: " + peer.getFilename());
			
			outToPeer.close();
			inToClient.close();
			toDisk.close();
			
		} catch (UnknownHostException e) 
		{
			e.printStackTrace();
		} catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	private void update() throws IOException, ClassNotFoundException
	{		
		out.writeObject(new MessageHandler(Message.UPDATE, fetchLocalFiles()));
		MessageHandler msg = (MessageHandler) in.readObject();
		
		if(msg.getMessage() != Message.UPDATE_ACK)
			error("Protocol violation: did not receive update ack");
		else
			println("Update successfull!");
	}
	
	private ArrayList<String> fetchLocalFiles()
	{	
		File dir = new File(sharedPath);
		if(!dir.exists())
			dir.mkdir();
		
		File[] files = dir.listFiles();
		ArrayList<String> fileNames = new ArrayList<String>(files.length);
		
		for(File file: files)
			fileNames.add(file.toString());

		return fileNames;
	}
	
	private void exit() throws IOException
	{
		in.close();
		out.close();
		inFromClient.close();
	}
	
	private void print(String message)
	{
		System.out.print(message);
	}
	
	private void println(String message)
	{
		System.out.println(message);
	}
	
	private void error(String reason)
	{
		System.out.println("Error: " + reason);
	}
	
	private String ip;
	private int portNumber;
	private String downloadPath;
	private String sharedPath;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Scanner inFromClient;
	
	public static void main(String[] args) 
	{
		Client c;
		
		if(args.length == 0)
		{
			c = new Client();
			new Thread(c).start();
		}
	}
}