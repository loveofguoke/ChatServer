import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

interface ClientListener {
  void msgRcvd(String msg);
  void clientQuit(Client c);
}

public class Server implements ClientListener, Runnable {
  public static final int PORT = 3936;
  public static final int SUBSELNUM = 3;
  private static Server theServer = new Server();
  LinkedList<Client> lstClient = new LinkedList<Client>();
  private Queue<String> msgQueue = new ArrayDeque<String>();
  private ExecutorService executer = Executors.newCachedThreadPool();
  private Selector selector;
  private Selector[] subSelectors = new Selector[SUBSELNUM];
  private int next = 0;
  private ServerSocketChannel serverSocket;

  private Server() { // MainReactor
    try {
      selector = Selector.open();
      for(int i = 0; i < subSelectors.length; i++) {
        subSelectors[i] = Selector.open();
      }
      serverSocket = ServerSocketChannel.open();
      serverSocket.bind(new InetSocketAddress(PORT));
      serverSocket.configureBlocking(false);
      System.out.println("Started: " + serverSocket);
      SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
      sk.attach(new Acceptor());        
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected static Server getServer() {
    return theServer;
  }
  synchronized void sendMsg(String msg) {
    msgQueue.add(msg);
    this.notify();
  }

  @Override
  public void msgRcvd(String msg) {
    sendMsg(msg);
  }

  @Override
  synchronized public void clientQuit(Client c) {
    lstClient.remove(c);
    // c.stopRx();
  }
  
  void go() throws IOException {
    try { 
      Runnable queueRunner = this;
      executer.submit(queueRunner);
      for (int i = 0; i < SUBSELNUM; i++) {
        executer.submit(new SubReactor(subSelectors[i]));
      }
      while (true) {
        selector.select();
        Set selected = selector.selectedKeys();
        Iterator it = selected.iterator();
        while (it.hasNext()) {
          dispatch((SelectionKey)(it.next()));
        }
        selected.clear();
      }
    } catch (IOException e) {
    } finally {
      executer.shutdown();
      serverSocket.close();
    }
  } 

  void dispatch(SelectionKey k) {
    Runnable r = (Runnable)(k.attachment());
    if (r != null) {
      r.run();
    }
  }

  @Override
  public void run() {
      while (true) {
        synchronized (this) {
          try {
            while (msgQueue.isEmpty()) {
              this.wait();
            }
            for (String msg : msgQueue) {
              for (Client c : lstClient) {
                // May require new thread
                // c.sendMsg(msg);
                executer.submit(new Sender(c, msg));
              }
              msgQueue.poll(); // right?
            }
          } catch (InterruptedException e) {}
        }
      }
  }

  class Acceptor implements Runnable { // inner
    @Override
    public void run() {
        try {
            SocketChannel sc = serverSocket.accept();
            // System.out.println("Connection accepted: " + sc);
            if (sc != null) {
              Client c = new Client(subSelectors[next], sc);
              if (++next == subSelectors.length) next = 0;
              c.addMsgListener(theServer);
              synchronized (theServer) {
                lstClient.add(c);
              }
              // executer.submit(c);
            }
        } catch(IOException ex) {}
    }
  }

  class SubReactor implements Runnable {
    private Selector subSelector;

    SubReactor(Selector sel) {
      subSelector = sel;
    }

    @Override
    public void run() {
      try {
        while (true) {
          subSelector.select();
          Set selected = subSelector.selectedKeys();
          Iterator it = selected.iterator();
          while (it.hasNext()) {
            dispatch((SelectionKey) (it.next()));
          }
          selected.clear();
        }
      } catch (IOException e) {
      }
    }
  }
  
  public static void main(String[] args) throws IOException {
    theServer.go();
  }
}

class Sender implements Runnable {
  Client c;
  String msg;

  Sender(Client c, String msg) {
    this.c = c;
    this.msg = msg;
  }

  @Override
  public void run() {
      c.sendMsg(msg);
  }
}