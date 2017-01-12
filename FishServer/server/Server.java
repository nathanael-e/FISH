package fish.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import fish.common.FishFile;
import fish.common.MessageHandler;

public class Server implements Runnable	
{
	final static int DEFAULT_PORT_NUMBER = 6123;
	final static int HITS_PER_PAGE = 25;
	
	Server() 
	{
		this(DEFAULT_PORT_NUMBER);
	}
	
	Server(int portNumber) 
	{
		this.portNumber = portNumber;
	}
	
	protected boolean put(Socket client, ArrayList<String> files)
	{
		String ip = client.getInetAddress().getHostAddress();
		System.out.println(ip);
		String port = String.valueOf(client.getPort());
		
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		
		try (IndexWriter w = new IndexWriter(fishIndex, config))
		{	
			for(String path:files)
			{
				String[] fragments = path.split("/");
				String filename = fragments[fragments.length - 1];
				addDoc(w, filename, path, ip, port);
			}
		} catch (IOException e) 
		{
			return false;
		}
		
		return true;
	}
	
	protected boolean removeAll(String ip) 
	{
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		
		try(IndexWriter w = new IndexWriter(fishIndex, config))
		{
			Query q = new QueryParser("ip", analyzer).parse(ip);
			w.deleteDocuments(q);
		}
		catch (IOException | ParseException e) 
		{
			return false;
		}
		
		return true;
	}
	
	protected ArrayList<FishFile> search(String keyword) 
	{		
		ArrayList<FishFile> files = new ArrayList<FishFile>();
		
		try 
		{
			IndexSearcher searcher = new 
					IndexSearcher(DirectoryReader.open(fishIndex));
			
			Query q = new QueryParser("filename", analyzer).parse(keyword);
			TopDocs docs = searcher.search(q, HITS_PER_PAGE);
			ScoreDoc[] hits = docs.scoreDocs;
			
			for(ScoreDoc hit:hits)
			{
				Document d = searcher.doc(hit.doc);
				files.add(new FishFile(d.get("filename"), d.get("path"), d.get("ip"), Integer.parseInt((d.get("port")))));
			}
	
		} catch (IOException | ParseException e) 
		{
			e.printStackTrace();
			return null;
		}
		
		return files;
	}
		
	private void addDoc(IndexWriter w, String filename, String path, String ip, String port) throws IOException
	{
		Document doc = new Document();
		doc.add(new TextField("filename", filename, Field.Store.YES));
		doc.add(new StringField("path", path, Field.Store.YES));
		doc.add(new StringField("ip", ip, Field.Store.YES));
		doc.add(new StringField("port", port, Field.Store.YES));
		w.addDocument(doc);
	}

	public void run() 
	{
		try(ServerSocket socket = new ServerSocket(portNumber))
		{
			analyzer = new StandardAnalyzer();
			fishIndex = new RAMDirectory();
			
			while(true)
			{
				Socket client = socket.accept();
				new Thread(new ClientHandler(this, client)).start();	
			}
		} catch (IOException e) 
		{
			System.out.println("Server has crashed...");
			e.printStackTrace();	
		}
	}
	
	private int portNumber;
	private Directory fishIndex;
	private StandardAnalyzer analyzer;
	
	public static void main(String[] args) 
	{
		Server s;
		
		if(args.length == 0)
		{
			s = new Server();
			new Thread(s).start();
			System.out.println("Server is running with default arguments");
		}
	}
}