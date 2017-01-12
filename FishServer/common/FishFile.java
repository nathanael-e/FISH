package fish.common;

import java.io.Serializable;

public class FishFile implements Serializable 
{
	public FishFile(String filename, String path, String ip, int port)
	{
		this.filename = filename;
		this.path = path;
		this.ip = ip;
		this.port = port;
	}
	
	public String getIp()
	{
		return ip;
	}
	
	public int getPortNumber()
	{
		return port;
	}
	
	public String getFilename()
	{
		return filename;
	}
	
	public String getPath()
	{
		return path;
	}

	private String ip;
	private String path;
	private int port;
	private String filename;
}
