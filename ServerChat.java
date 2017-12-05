import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.io.*;
import java.nio.*;


public class ServerChat {
    private Selector selector;
    private InetSocketAddress listenAddress;


    private Hashtable<String, String> admin = new Hashtable<>();
    private Hashtable<String, SelectionKey> online_users = new Hashtable<>(); //username -> key
    //private HashSet<SelectionKey> 
    public static void main(String[] args) throws Exception {

	     try {
	        new ServerChat("localhost", 6001).startServer();
	       } catch (IOException e) {
	          e.printStackTrace();
	         }
         }

    public ServerChat(String address, int port) throws IOException {
	     listenAddress = new InetSocketAddress(port);
    }

    // create server channel
    private void startServer() throws IOException {
	     this.selector = Selector.open();
       ServerSocketChannel serverChannel = ServerSocketChannel.open();

	     // retrieve server socket and bind to port
	     serverChannel.socket().bind(listenAddress);
       serverChannel.configureBlocking(false);
	     serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
	     System.out.println("Server started...");

	      while (true) {
	         // wait for events
	          this.selector.select();
	           //work on selected keys
	            Set<SelectionKey> keys = this.selector.selectedKeys();
              //Set<SelectionKey> temp = new HashSet<SelectionKey>();
              //temp.addAll(keys);
              
	             for(Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
                   SelectionKey key = iter.next();
                   iter.remove();
		               if (!key.isValid()) {
		                   continue;
                     }
                   if (key.isAcceptable()) { 
                     this.accept(key);
		                 }
                  else {
                    try {
		                  if (key.isReadable()) { //if the key is ready for read
                        String command = this.read(key);
                        this.process_command(key, command);
		                  }
                    } catch(IOException e) {}
                  }
                }
                  
	         }
	      }

    //process command
    private void process_command(SelectionKey key, String command) throws IOException {
      //String command = this.read(key);
      String args[] = command.split(" ");
      if(args[0].equalsIgnoreCase("register")) {
        if(args.length != 3) {
          System.out.println("client entered a wrong command!");
          this.write(key, "register command should be: register [username] [password]");
        }
        else { //correct num args
          String username = args[1];
          String password = args[2];
          //check validation
          if(username.length() < 4 || username.length() > 8 || !username.matches("[a-zA-Z0-9]*")) {
            //System.out.println("username is invalid!");
            this.write(key, "Username is invalid!");
          }
          else if(password.length() < 4 || password.length() > 8 || !password.matches("[a-zA-Z0-9]*")) {
            //System.out.println("password does not match!");
            this.write(key, "password is invalid!");
          }
          else {
            admin.put(username, password);
            System.out.println("client register successfull!");
            this.write(key, "Register Successfull!");
          }
        }
      }
      else if(args[0].equalsIgnoreCase("login")) {
        if(args.length != 3) {
          System.out.println("client entered a wrong command!");
          this.write(key, "login command should be: login [username] [password]");
        }
        else if(online_users.containsKey(args[1])) { //this user already logged in
          System.out.println("multiple logins for user " + args[1]);
          this.write(key, "user " + args[1] + " already logged in.");
        }
        else {  //correct
          String username = args[1];
          String password = args[2];
          //check validation
          if(!admin.containsKey(username)) {
            this.write(key, "Username does not exist!");
          }
          else if(!admin.get(username).equals(password)) {
            this.write(key, "Password does not match the given username!");
          }
          else {
            System.out.println("client login successful");
            this.write(key, "Login Successful!");
            this.write(key, "Welcome to Hoosier Chatroom!");
            //if this client is already login, overwrite the previous login with the current one.
            if(online_users.containsValue(key)) { 
              online_users.remove(this.getUsername(online_users, key));
            }
            online_users.put(username, key);
          }
        }
      }
      else if(args[0].equalsIgnoreCase("send")) {  //1.send [username] [message] 2.send[message]
        if(args.length < 2) {
          this.write(key, "send command should be: send [username] [message] or send [message]");
        }
        else if(!online_users.containsValue(key)) { //if user is offline
            this.write(key, "Please login first!");
        }
        else { //if user is online
          String message = "";
          String username = this.getUsername(online_users, key);
          if(admin.containsKey(args[1])) { //private message
            //String private_user = args[1];
            if(online_users.containsKey(args[1])) { //if this user is online
              if(username.equals(args[1])) {
                System.out.println("client sent message to himself! wrong operation!");
                this.write(key, "You can not sent message to yourself!");
              }
              else {
                message += "[From " + username + "]:\n";
                for(int i = 2; i < args.length; i++) {
                  message += args[i] + " ";
                }
                this.write(online_users.get(args[1]), message);
              }
            }
            else {  //user is offline or does not exsit
              System.out.println("client sent message to invalid user.");
              this.write(key, "user " + args[1] + " is not online.");
            }
          }
          else { //public message
            message += "[From " + username + "]:\n";
            for(int i = 1; i < args.length; i++) {
              message += args[i] + " ";
            }
            this.writeToAll(key, message);
            System.out.println("client sent a public message.");
          }
        }
      }
      else if(args[0].equalsIgnoreCase("senda")) { //Send an anonymous msg
        if(args.length < 2) {
          this.write(key, "senda command should be: senda [username] [message] or senda [message]");
        }
        else if(!online_users.containsValue(key)) { //if user if offline
            this.write(key, "Please login first!");
        }        
        else { //if online
          String message = "";
          String username = this.getUsername(online_users, key);
          if(admin.containsKey(args[1])) { //private message
            if(online_users.containsKey(args[1])) { //if online
               if(username.equals(args[1])) {
                System.out.println("client sent message to himself! wrong operation!");
                this.write(key, "You can not sent message to yourself!");
              }
              else {           
               message += "[From anonymous]:\n";
               for(int i = 2; i < args.length; i++) {
                 message += args[i] + " ";
               }
               this.write(online_users.get(args[1]), message);
               System.out.println("client sent an anonymous private message.");
             }
            }
            else { //if offline or user not exist
              System.out.println("client sent message to invalid user");
              this.write(key, "User " + args[1] + " is not online");
            }
          }
          else { //public message
            message += "[From anonymous]:\n";
            for(int i = 1; i < args.length; i++) {
              message += args[i] + " ";
            }
            this.writeToAll(key, message);
            System.out.println("client sent an anonymous public message");
          }
        }
      }
      else if(args[0].equalsIgnoreCase("list")) { //list all online users
        //check online
        if(!online_users.containsValue(key)) { //have not logged in yet
          this.write(key, "Please login first!");
        }
        else {
          String listOfUsers = "";
          for(String username: online_users.keySet()) {
            listOfUsers += username + "\n";
          }
          this.write(key, listOfUsers);
        }
      }
      else if(args[0].equalsIgnoreCase("logout")) {
        if(!online_users.containsValue(key)) { //have not logged in yet
          this.write(key, "Please login first!");
        }
        else {
          System.out.println("user " + this.getUsername(online_users, key) + " logged out");
          online_users.remove(this.getUsername(online_users, key));
          //System.out.println("current online users: " + online_users);
          this.write(key, "You have successfully logged out!");
          key.channel().close();
        }        
      }
      else if(args[0].equalsIgnoreCase("sendf")) {
        if(!online_users.containsValue(key)) { //have not logged in yet
          this.write(key, "Please login first!");
        }
        else {
          String file_message = "$file content$\n";
          file_message += command.substring(7, command.length());
          System.out.println("client sent a file to all online users.");
          System.out.println("file content: " + file_message);
          this.writeToAll(key, file_message);
        }
        
      }
      else {
        this.write(key, "Wrong command!");
        System.out.println("client entered a wrong command.");
      }
    }



    //accept a connection made to this channel's socket
    private void accept(SelectionKey key) throws IOException {

      ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
    	SocketChannel channel = serverChannel.accept();
    	channel.configureBlocking(false);
    	Socket socket = channel.socket();
    	SocketAddress remoteAddr = socket.getRemoteSocketAddress();
    	System.out.println("Connected to: " + remoteAddr);

	    // register channel with selector for further IO
      int operations = SelectionKey.OP_READ
        | SelectionKey.OP_WRITE;
      channel.register(this.selector, operations);
	    //channel.register(this.selector, SelectionKey.OP_READ);
      
    }
    private void accept2(SelectionKey key) throws IOException {

      ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
      SocketChannel channel = serverChannel.accept();
      channel.configureBlocking(false);
      // register channel with selector for further IO
      int operations = SelectionKey.OP_READ
        | SelectionKey.OP_WRITE;
      channel.register(this.selector, operations);
      
    }

    //read from the socket channel
    private String read(SelectionKey key) throws IOException {
    	SocketChannel channel = (SocketChannel) key.channel();
    	ByteBuffer buffer = ByteBuffer.allocate(1024);
    	int numRead = -1;

      numRead = channel.read(buffer);

    	if (numRead == -1) {
    	    Socket socket = channel.socket();
    	    //SocketAddress remoteAddr = socket.getRemoteSocketAddress();
    	    //System.out.println("Connection closed by client: " + remoteAddr);
    	    channel.close();
    	    key.cancel();
    	    return "";
    	}
      byte[] data = new byte[numRead];
      System.arraycopy(buffer.array(), 0, data, 0, numRead);
      System.out.println("Got: " + new String(data));   
      return new String(data); 	
    }

  private void writeToAll(SelectionKey key_sent, String message) throws IOException{
    for (SelectionKey key : selector.keys()) { 
      if(key.equals(key_sent)) {  //do not send to the client who sent this msg
        continue;
      }
      if(!online_users.containsValue(key)) { //do not send to offline users
        continue;
      }
      this.write(key, message);
    }
  }


  private void write(SelectionKey key, String message) throws IOException {
    SocketChannel channel = (SocketChannel) key.channel();
    ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
    channel.write(buffer); 
    //System.out.println("write: " + message + " to client ");
  }

  private String getUsername(Hashtable<String, SelectionKey> users, SelectionKey key) {
    for(Map.Entry<String, SelectionKey> entry : users.entrySet()) {
        if(entry.getValue().equals(key)) {
            return entry.getKey();
        }
    }
    return "unknownUser";
  }


}

 







