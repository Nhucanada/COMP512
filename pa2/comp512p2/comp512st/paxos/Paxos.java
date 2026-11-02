package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;

import comp512.utils.*;

// Any other imports that you may need.
import java.io.*;
import java.net.ServerSocket;
import java.util.Collections;
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

	// helper class to track state and responses for an active ballot attempt
	class BallotContext {
		int ballotNum;
		CountDownLatch promiseLatch;
		Set<String> promisedProcs = Collections.synchronizedSet(new HashSet<>());
		int highestOtherAcceptedBallot = -1;
		Object highestOtherAcceptedValue = null;

		CountDownLatch acceptedLatch;
		Set<String> acceptedProcs = Collections.synchronizedSet(new HashSet<>());

		public BallotContext(int ballotNum, int totalProcesses) {
			this.ballotNum = ballotNum;
			int majority = (totalProcesses / 2) + 1;
			this.promiseLatch = new CountDownLatch(majority);
			this.acceptedLatch = new CountDownLatch(majority);
		}
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
			case"PROPOSE":
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
				break;
			case "PROMISE":
				break;
			case "ACCEPT":
			{
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				int currPromise = highestAcceptedBallot.getOrDefault(inst, -1); //get inst, or default to -1

				if (balNum >= currPromise)
				{
					highestPromisedBallot.put(inst, balNum);
					highestAcceptedBallot.put(inst, balNum);
					acceptedValues.put(inst, msg.value); //todo: fix
					failCheck.checkFailure(myProcess, "accepted");

				}
			}
			break;
			case "ACCEPTED":
				// Handled synchronously inside runConsensusInstance
				break;

			default:
				break;
		}

	}
	// paxos recieves msg (move) from app layer. wants to add move to total order.
	public void broadcastTOMsg(Object val)
	{
		// This is just a place holder.
		// Extend this to build whatever Paxos logic you need to make sure the messaging system is total order.
		// Here you will have to ensure that the CALL BLOCKS, and is returned ONLY when a majority (and immediately upon majority) of processes have accepted the value.
		failCheck.checkFailure(myProcess, "broadcastTOMsg_entry");

		int instanceToRun;
		synchronized (this) {
			instanceToRun = currInstance++;
		}

		Object chosenValue = runConsensus(instanceToRun, val);

		// Push successfully agreed-upon message to local delivery queue
		try {
			deliveredQueue.put(chosenValue);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// keep running paxos until a majority is reached
	private Object runConsensus (int instance, Object proposedVal)
	{
		int majority = (allValidProcesses.length / 2 ) + 1;
		int localBallotSeq = 0;


		while (running) {
			failCheck.checkFailure(myProcess, "consensus_loop_start");

			// Generate unique ballot number
			localBallotSeq++;
			int ballotNum = localBallotSeq * 100 + Math.abs(myProcess.hashCode() % 100);

			// collect responses for this ballot attempt
			Set<String> promisedProcs = new HashSet<>();
			int highestOtherAcceptedBallot = -1;
			Object selectedVal = proposedVal;

			// --- PHASE 1A: Send PROPOSE ---
			PaxosMessage prepareMsg = new PaxosMessage("PREPARE", instance, ballotNum, myProcess, null, -1);

			// We can intercept/collect responses using a temporary synchronous collector or queue.
			// For simplicity and robustness with GCL, we listen on a specialized collector queue or filter.
			// (A clean implementation can use synchronized ballot response counters).

			// Broadcast prepare via GCL
			gcl.broadcastMsg(prepareMsg);

			// Wait/collect promises from majority...

			// --- PHASE 2A: Send ACCEPT ---
			// If phase 1 succeeds, broadcast ACCEPT with selectedVal
			// Collect ACCEPTED acknowledgments from majority...

			// For brevity in a robust framework template, once majority ACK is achieved:
			return selectedVal;
		}
		return proposedVal;

	}

	// This is what the application layer is calling to figure out what is the next message in the total order.
	// Messages delivered in ALL the processes in the group should deliver this in the same order.

	public Object acceptTOMsg() throws InterruptedException {
		failCheck.checkFailure(myProcess, "acceptTOMsg");
		return deliveredQueue.take();
	}

	public void shutdownPaxos() {
		running = false;
		if (listenerThread != null) {
			listenerThread.interrupt();
		}
		gcl.shutdownGCL();
	}
}

