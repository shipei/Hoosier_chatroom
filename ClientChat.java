import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;

public class ClientChat {

  public static void main(String[] args) throws IOException, InterruptedException {
      
    InetSocketAddress hostAddress = new InetSocketAddress("52.15.113.129", 6001);
    SocketChannel client = SocketChannel.open(hostAddress);
    //client.configureBlocking(false);

    new Thread(new SendThread(client)).start();
    new Thread(new ListenThread(client)).start();
        
    }
}


    class ListenThread implements Runnable {

      public SocketChannel client;
      public ListenThread(SocketChannel client) {
        this.client = client;
      }


     @Override
      public void run() {
        boolean logout = false;
        String msg = "";
        while(true) {
          if(client.isConnected()) {
            try {
              System.out.print("--");
              msg = this.read(client);
            } catch(IOException e) {}
            if(msg.length() == 0) {
              System.out.println("server error1");
              break;
            }
            if(msg.contains("logged out")) {
              try {
                client.close();
                logout = true;
                break;
              } catch (IOException e) {}
            }
            else if(msg.contains("$file content$")) {
              String lines[] = msg.split("\n");
              String file_name = lines[1];
              // String file_content = msg.substring(14, msg.length());
              String file_content = "";
              for(int i = 2; i < lines.length; i++) {
                file_content += lines[i] + "\n";
              }
              this.save_file(client, file_name, file_content);
              System.out.println("file is saved on local directory.");  
            }
            if(logout) break;
            System.out.println(msg);
          }
          else {
            System.out.println("server error 2");
            break;
          }
        }
      }

      private void save_file(SocketChannel client, String file_name, String file_content) {
        FileWriter fw = null;
        BufferedWriter bw = null;
        try {
          fw = new FileWriter("download.txt");
          bw = new BufferedWriter(fw);
          bw.write(file_content);
          System.out.println("Done");
        } catch(IOException e) {
          e.printStackTrace();
        } finally {
          try {
            if (bw != null) bw.close();
            if (fw != null) fw.close();
          } catch(IOException e) {
            e.printStackTrace();
          }
        }
      }

      private String read(SocketChannel client) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        int numRead = -1;
        numRead = client.read(buffer);
        if(numRead == -1) {
          return "";
        }
        byte[] data = new byte[numRead];
        System.arraycopy(buffer.array(), 0, data, 0, numRead);
        //System.out.println(new String(data));
        return new String(data);
      }
    }

  class SendThread implements Runnable {

    public SocketChannel client;
      public SendThread(SocketChannel client) {
        this.client = client;
      }
    @Override
    public void run() {
      Scanner scan = new Scanner(System.in);
      while(scan.hasNextLine()) {
        //writing command to server
        String command = scan.nextLine();
        if(command.length() == 0) {
          continue;
        }
        try {
          if (command.contains("sendf")) {
            String args[] = command.split(" ");
            this.send_file(client, args[1]);
          }
          else {
            this.write_command(client, command);
          }
        } catch (IOException e) {}
      }
    }

    private void send_file(SocketChannel client, String file_path) throws IOException {
      //Socket socket = (SelectionKey) key.channel().socket();
      //DataOutputStream output = new DataOutputStream(socket.getOutputStream());
      File file = new File(file_path);
      FileReader fileReader = new FileReader(file);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      StringBuffer sb = new StringBuffer();
      sb.append("sendf \n");
      sb.append(file_path + "\n");
      String line;
      while((line = bufferedReader.readLine()) != null) {
        sb.append(line);
        sb.append("\n");
      }
      fileReader.close();
      String file_content = sb.toString();
      // System.out.println("send: " + file_content);
      byte[] data = new String(file_content).getBytes();
      ByteBuffer buffer = ByteBuffer.wrap(data);
      client.write(buffer);
    }

    private void write_command(SocketChannel client, String command) throws IOException {
      byte[] data = new String(command).getBytes();
      ByteBuffer buffer = ByteBuffer.wrap(data);
      client.write(buffer);
    }
  }


    
		  
