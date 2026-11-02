package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;

import comp512.utils.*;

// Any other imports that you may need.
import java.io.*;
import java.net.ServerSocket;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.logging.*;
import java.net.UnknownHostException;




// ANY OTHER classes, etc., that you add must be private to this package and not visible to the application layer.

// message wrapper to ensure paxos protocol
class PaxosMessage implements Serializable{

	private static final long serialVersionID = 1L;

	// todo: change to enum??
	String type;    //"PROPOSE", "PROMISE", "ACCEPT", "ACCEPTED"

	int instance;
	int ballotNum;
	String sender;
	Object value;
	int acceptedBal;

	public PaxosMessage(String type, int instance, int ballotNumber, String sender, Object value, int acceptedBallot) {
		this.type = type;
		this.instance = instance;
		this.ballotNum = ballotNumber;
		this.sender = sender;
		this.value = value;
		this.acceptedBal = acceptedBallot;
	}

}

// extend / implement whatever interface, etc. as required.
// NO OTHER public members / methods allowed. broadcastTOMsg, acceptTOMsg, and shutdownPaxos must be the only visible methods to the application layer.
//You should also not change the signature of these methods (arguments and return value) other aspects maybe changed with reasonable design needs.
public class Paxos
{
	GCL gcl;
	FailCheck failCheck;
	Logger logger;
	String myProcess;
	String[] allValidProcesses;



	private int currInstance = 0;

	// NOTE: concurrentHashMaps are thread safe
	private final Map<Integer, Integer> highestPromisedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> highestAcceptedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> acceptedValues = new ConcurrentHashMap<>();


	//blocking queue to ensure TO
	private final BlockingQueue<Object> deliveredQueue = new LinkedBlockingQueue<>();

	private Thread listenerThread;
	private volatile boolean running = true; // volatile booleans are

	int ballotID;

	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException
	{
		// Remember to call the failCheck.checkFailure(...) with appropriate arguments throughout your Paxos code to force fail points if necessary.
		this.failCheck = failCheck;

		// Initialize the GCL communication system as well as anything else you need to.
		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger) ;
		this.logger = logger;

		this.myProcess = myProcess;

		this.allValidProcesses = allGroupProcesses;

		//start listening for msgs
		this.listenerThread = new Thread(this::listenLoop);
		this.listenerThread.start();
	}

	//loop to listen for incoming msgs
	private void listenLoop(){

		try{
			while (running){
				GCMessage gcMsg = gcl.readGCMessage();
				if (gcMsg == null || gcMsg.val == null)
				{
					break;
				}

				if (gcMsg.val instanceof PaxosMessage)
				{
					PaxosMessage pMsg = (PaxosMessage) gcMsg.val;
					handlePaxosMsg(pmsg);

				}

			}
		} catch (InterruptedException e) {

		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error in Paxos Listening Loop: ", e );
		}
	}

	// synchronised: can only be used by 1 thread at a time
	private synchronized void handlePaxosMsg (PaxosMessage msg)
	{

		//TODO: fix
		failCheck.checkFailure(myProcess, "handlePaxosMessage");

		switch (msg.type)
		{
			case("PROPOSE"):
			{
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				int currPromise = highestAcceptedBallot.getOrDefault(inst, -1); //get inst, or default to -1

				if (balNum > currPromise)
				{
					highestPromisedBallot.put(inst, balNum);
					failCheck.checkFailure(myProcess,"propose"); //TODO

					// promise highest value

					int accepted = highestAcceptedBallot.getOrDefault(inst, -1);
					Object accVal = acceptedValues.get(inst);
					//create new promise msg
					PaxosMessage promise = new PaxosMessage("PROMISE", inst, balNum, myProcess, accVal, accepted);
					gcl.sendMsg(promise, msg.sender);

				}
			}

		}


	}




	// paxos recieves msg (move) from app layer. wants to add move to total order.
	public void broadcastTOMsg(Object val)
	{
		// This is just a place holder.
		// Extend this to build whatever Paxos logic you need to make sure the messaging system is total order.
		// Here you will have to ensure that the CALL BLOCKS, and is returned ONLY when a majority (and immediately upon majority) of processes have accepted the value.
		gcl.broadcastMsg(val);
	}

	// This is what the application layer is calling to figure out what is the next message in the total order.
	// Messages delivered in ALL the processes in the group should deliver this in the same order.
	//
	public Object acceptTOMsg() throws InterruptedException
	{
		Queue<String> buffer = new LinkedList<>();



		// This is just a place holder.
		GCMessage gcmsg = gcl.readGCMessage();
		return gcmsg.val;
	}


	// Add any of your own shutdown code into this method.
	public void shutdownPaxos()
	{
		gcl.shutdownGCL();
	}

	private boolean propose()
	{
		gcl.broadcastMsg("propose");

		return false;
	}



}

