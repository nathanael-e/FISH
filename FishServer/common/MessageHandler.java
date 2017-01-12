package fish.common;

import java.io.Serializable;
import java.util.ArrayList;

public class MessageHandler implements Serializable 
{	
	public MessageHandler(Message msg, Serializable content)
	{
		this.msg = msg;
		this.content = content;
	}
	
	public Message getMessage()
	{
		return msg;
	}
	
	public Serializable getContent()
	{
		return content;
	}
	
	private Message msg;
	private Serializable content;
}