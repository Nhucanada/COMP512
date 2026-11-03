package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;

import comp512.utils.*;

// Any other imports that you may need.
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.*;
import java.net.UnknownHostException;

// ANY OTHER classes, etc., that you add must be private to this package and not visible to the application layer.

// Wrapper to uniquely identify each proposed application value so no move is dropped or duplicated
class ProposalValue implements Serializable {
	private static final long serialVersionUID = 1L;

	final String proposer;
	final long moveSeq;
	final Object appVal;

	ProposalValue(String proposer, long moveSeq, Object appVal) {
		this.proposer = proposer;
		this.moveSeq = moveSeq;
		this.appVal = appVal;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ProposalValue)) return false;
		ProposalValue that = (ProposalValue) o;
		return moveSeq == that.moveSeq && Objects.equals(proposer, that.proposer);
	}

	@Override
	public int hashCode() {
		return Objects.hash(proposer, moveSeq);
	}

	@Override
	public String toString() {
		String valStr = (appVal instanceof Object[]) ? Arrays.toString((Object[]) appVal) : String.valueOf(appVal);
		return String.format("Move[by=%s,seq=%d,val=%s]", proposer, moveSeq, valStr);
	}
}

// message wrapper to ensure paxos protocol
class PaxosMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	String type;    // "PROPOSE", "PROMISE", "REJECT", "ACCEPT", "ACCEPTED", "CONFIRM"
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

	@Override
	public String toString() {
		return "PaxosMessage{" +
				"type='" + type + '\'' +
				", instance=" + instance +
				", ballotNum=" + ballotNum +
				", sender='" + sender + '\'' +
				", value=" + value +
				", acceptedBal=" + acceptedBal +
				'}';
	}
}

// helper class to track state and responses for an active ballot attempt
class BallotContext {
	final int ballotNum;
	final int majority;
	final CountDownLatch promiseLatch;
	final Set<String> promisedProcs = Collections.synchronizedSet(new HashSet<>());
	final Set<String> rejectedProcs = Collections.synchronizedSet(new HashSet<>());
	volatile int highestOtherAcceptedBallot = -1;
	volatile Object highestOtherAcceptedValue = null;
	volatile int highestRejectedBallot = -1;
	volatile boolean preempted = false;

	final CountDownLatch acceptedLatch;
	final Set<String> acceptedProcs = Collections.synchronizedSet(new HashSet<>());

	public BallotContext(int ballotNum, int totalProcesses) {
		this.ballotNum = ballotNum;
		this.majority = (totalProcesses / 2) + 1;
		this.promiseLatch = new CountDownLatch(majority);
		this.acceptedLatch = new CountDownLatch(majority);
	}

	public void abort() {
		this.preempted = true;
		while (promiseLatch.getCount() > 0) {
			promiseLatch.countDown();
		}
		while (acceptedLatch.getCount() > 0) {
			acceptedLatch.countDown();
		}
	}
}

// extend / implement whatever interface, etc. as required.
// NO OTHER public members / methods allowed. broadcastTOMsg, acceptTOMsg, and shutdownPaxos must be the only visible methods to the application layer.
// You should also not change the signature of these methods (arguments and return value) other aspects maybe changed with reasonable design needs.
public class Paxos
{
	GCL gcl;
	FailCheck failCheck;
	Logger logger;
	String myProcess;
	String[] allValidProcesses;
	private int myProcessId;

	private int nextDeliveryInstance = 0;
	private final Object commitLock = new Object();
	private final AtomicLong moveSeqGenerator = new AtomicLong(0);
	private final Random rand = new Random();

	// NOTE: concurrentHashMaps are thread safe
	private final Map<Integer, Integer> highestPromisedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> highestAcceptedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Object> acceptedValues = new ConcurrentHashMap<>();
	private final Map<Integer, Object> committedValues = new ConcurrentHashMap<>();

	private final Map<Integer, BallotContext> activeBallots = new ConcurrentHashMap<>();

	// blocking queue to ensure TO
	private final BlockingQueue<Object> deliveredQueue = new LinkedBlockingQueue<>();

	private Thread listenerThread;
	private volatile boolean running = true;

	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException
	{
		// Remember to call the failCheck.checkFailure(...) with appropriate arguments throughout your Paxos code to force fail points if necessary.
		this.failCheck = failCheck;

		// Initialize the GCL communication system as well as anything else you need to.
		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger);
		this.logger = logger;
		this.myProcess = myProcess;
		this.allValidProcesses = allGroupProcesses;

		int idx = 1;
		for (int i = 0; i < allGroupProcesses.length; i++) {
			if (allGroupProcesses[i].equals(myProcess)) {
				idx = i + 1;
				break;
			}
		}
		this.myProcessId = idx;

		this.logger.info("[PAXOS INIT] Initialized Paxos for process: " + myProcess + " (id=" + myProcessId + ")");

		// start listening for msgs
		this.listenerThread = new Thread(this::listenLoop);
		this.listenerThread.start();
	}

	// loop to listen for incoming msgs
	private void listenLoop() {
		try {
			while (running) {
				GCMessage gcMsg = gcl.readGCMessage();
				if (gcMsg == null || gcMsg.val == null) {
					break;
				}

				if (gcMsg.val instanceof PaxosMessage) {
					PaxosMessage pmsg = (PaxosMessage) gcMsg.val;
					handlePaxosMsg(pmsg);
				}
			}
		} catch (InterruptedException e) {
			this.logger.info("[ListenLoop] Listener thread interrupted for process: " + myProcess);
		} catch (Exception e) {
			if (running) {
				this.logger.log(Level.SEVERE, "Error in Paxos Listening Loop: ", e);
			}
		}
	}

	// Record a committed value for an instance and deliver all contiguous instances in strict total order
	private void recordCommit(int inst, Object chosenValue) {
		synchronized (commitLock) {
			if (!committedValues.containsKey(inst)) {
				committedValues.put(inst, chosenValue);
				this.logger.info(String.format("[%s] Instance %d committed with value: %s", myProcess, inst, chosenValue));
			}

			while (committedValues.containsKey(nextDeliveryInstance)) {
				Object committed = committedValues.get(nextDeliveryInstance);
				Object appVal = (committed instanceof ProposalValue) ? ((ProposalValue) committed).appVal : committed;
				deliveredQueue.offer(appVal);
				this.logger.info(String.format("[%s] Delivered instance %d to application queue: %s",
						myProcess, nextDeliveryInstance,
						(appVal instanceof Object[]) ? Arrays.toString((Object[]) appVal) : String.valueOf(appVal)));
				nextDeliveryInstance++;
			}

			BallotContext ctx = activeBallots.get(inst);
			if (ctx != null) {
				ctx.abort();
			}
			commitLock.notifyAll();
		}
	}

	// synchronised: can only be used by 1 thread at a time
	private synchronized void handlePaxosMsg(PaxosMessage msg) {

		switch (msg.type) {
			case "PROPOSE": {
				failCheck.checkFailure(FailCheck.FailureType.RECEIVEPROPOSE);
				int inst = msg.instance;
				int balNum = msg.ballotNum;

				// If this instance is already committed, immediately inform the sender via CONFIRM
				if (committedValues.containsKey(inst)) {
					PaxosMessage confirmMsg = new PaxosMessage("CONFIRM", inst, balNum, myProcess, committedValues.get(inst), -1);
					gcl.sendMsg(confirmMsg, msg.sender);
					break;
				}

				int currPromise = highestPromisedBallot.getOrDefault(inst, -1);

				this.logger.info(String.format("[%s] Received PROPOSE for instance %d with ballot %d from %s", myProcess, inst, balNum, msg.sender));

				if (balNum > currPromise) {
					highestPromisedBallot.put(inst, balNum);

					// promise highest accepted value if any
					int accepted = highestAcceptedBallot.getOrDefault(inst, -1);
					Object accVal = acceptedValues.get(inst);

					this.logger.info(String.format("[%s] Promising ballot %d for instance %d. Sending PROMISE back to %s (highest accepted bal: %d)",
							myProcess, balNum, inst, msg.sender, accepted));

					PaxosMessage promise = new PaxosMessage("PROMISE", inst, balNum, myProcess, accVal, accepted);
					gcl.sendMsg(promise, msg.sender);
					failCheck.checkFailure(FailCheck.FailureType.AFTERSENDVOTE);

				} else {
					this.logger.info(String.format("[%s] REJECTED PROPOSE for instance %d with ballot %d (already promised ballot %d)",
							myProcess, inst, balNum, currPromise));
					PaxosMessage reject = new PaxosMessage("REJECT", inst, balNum, myProcess, null, currPromise);
					gcl.sendMsg(reject, msg.sender);
					failCheck.checkFailure(FailCheck.FailureType.AFTERSENDVOTE);
				}
				break;
			}
			case "PROMISE": {
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				BallotContext ctx = activeBallots.get(inst);

				if (ctx != null && ctx.ballotNum == balNum && !ctx.preempted) {
					if (ctx.promisedProcs.add(msg.sender)) {
						if (msg.acceptedBal > ctx.highestOtherAcceptedBallot && msg.value != null) {
							ctx.highestOtherAcceptedBallot = msg.acceptedBal;
							ctx.highestOtherAcceptedValue = msg.value;
						}
						ctx.promiseLatch.countDown();
						this.logger.info(String.format("[%s] Received PROMISE for instance %d, ballot %d from %s. Total promises: %d",
								myProcess, inst, balNum, msg.sender, ctx.promisedProcs.size()));
					}
				}
				break;
			}
			case "REJECT": {
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				int otherHigherBal = msg.acceptedBal;
				highestPromisedBallot.merge(inst, otherHigherBal, Math::max);

				BallotContext ctx = activeBallots.get(inst);
				if (ctx != null && ctx.ballotNum == balNum) {
					ctx.highestRejectedBallot = Math.max(ctx.highestRejectedBallot, otherHigherBal);
					ctx.rejectedProcs.add(msg.sender);
					this.logger.info(String.format("[%s] Received REJECT for instance %d, ballot %d from %s (promised ballot %d)",
							myProcess, inst, balNum, msg.sender, otherHigherBal));
					// Immediately preempt so we don't sit waiting on a 2000ms timeout
					ctx.abort();
				}
				break;
			}
			case "ACCEPT": {
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				int currPromise = highestPromisedBallot.getOrDefault(inst, -1);

				this.logger.info(String.format("[%s] Received ACCEPT request for instance %d, ballot %d from %s with value: %s",
						myProcess, inst, balNum, msg.sender, msg.value));

				// check against highest promised ballot
				if (balNum >= currPromise) {
					highestPromisedBallot.put(inst, balNum);
					highestAcceptedBallot.put(inst, balNum);
					acceptedValues.put(inst, msg.value);

					this.logger.info(String.format("[%s] Accepted value for instance %d, ballot %d. Sending ACCEPTED acknowledgment to %s",
							myProcess, inst, balNum, msg.sender));

					// Reply with ACCEPTED ack back to proposer
					PaxosMessage acceptedMsg = new PaxosMessage("ACCEPTED", inst, balNum, myProcess, msg.value, -1);
					gcl.sendMsg(acceptedMsg, msg.sender);
				} else {
					this.logger.info(String.format("[%s] REJECTED ACCEPT for instance %d, ballot %d (promised higher ballot %d)",
							myProcess, inst, balNum, currPromise));
					PaxosMessage reject = new PaxosMessage("REJECT", inst, balNum, myProcess, null, currPromise);
					gcl.sendMsg(reject, msg.sender);
				}
				break;
			}
			case "ACCEPTED": {
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				BallotContext ctx = activeBallots.get(inst);

				if (ctx != null && ctx.ballotNum == balNum && !ctx.preempted) {
					if (ctx.acceptedProcs.add(msg.sender)) {
						ctx.acceptedLatch.countDown();
						this.logger.info(String.format("[%s] Received ACCEPTED ack for instance %d, ballot %d from %s. Total accepts: %d",
								myProcess, inst, balNum, msg.sender, ctx.acceptedProcs.size()));
					}
				}
				break;
			}
			case "CONFIRM": {
				int inst = msg.instance;
				this.logger.info(String.format("[%s] Received CONFIRM for instance %d from %s with value: %s",
						myProcess, inst, msg.sender, msg.value));
				recordCommit(inst, msg.value);
				break;
			}
			default:
				this.logger.warning(String.format("[%s] Received unknown message type: %s", myProcess, msg.type));
				break;
		}
	}

	// paxos receives msg (move) from app layer. wants to add move to total order.
	public void broadcastTOMsg(Object val)
	{
		ProposalValue myProposal = new ProposalValue(myProcess, moveSeqGenerator.incrementAndGet(), val);
		this.logger.info(String.format("[%s] App requested broadcast for move: %s", myProcess, myProposal));

		while (running) {
			int instanceToRun;
			synchronized (commitLock) {
				instanceToRun = nextDeliveryInstance;
				while (committedValues.containsKey(instanceToRun)) {
					instanceToRun++;
				}
			}

			Object chosenValue = runConsensus(instanceToRun, myProposal);

			if (myProposal.equals(chosenValue)) {
				this.logger.info(String.format("[%s] Move %s successfully committed at instance %d", myProcess, myProposal, instanceToRun));
				return;
			} else {
				this.logger.info(String.format("[%s] Instance %d committed another player's move (%s); retrying %s in next instance",
						myProcess, instanceToRun, chosenValue, myProposal));
			}
		}
	}

	// keep running paxos for a given instance until a value is committed for that instance
	private Object runConsensus(int instance, Object proposedVal) {
		int localBallotSeq = 0;

		while (running) {
			if (committedValues.containsKey(instance)) {
				return committedValues.get(instance);
			}

			// Jump ballot sequence past any highest promised ballot we've already seen for this instance
			int highestSeen = highestPromisedBallot.getOrDefault(instance, 0);
			localBallotSeq = Math.max(localBallotSeq + 1, (highestSeen / 100) + 1);
			int ballotNum = localBallotSeq * 100 + myProcessId;

			this.logger.info(String.format("[%s] Starting consensus round for instance %d with generated ballot %d", myProcess, instance, ballotNum));

			BallotContext ctx = new BallotContext(ballotNum, allValidProcesses.length);
			activeBallots.put(instance, ctx);

			// 1. Send PROPOSE
			PaxosMessage proposeMsg = new PaxosMessage("PROPOSE", instance, ballotNum, myProcess, null, -1);
			this.logger.info(String.format("[%s] Broadcasting PROPOSE for instance %d, ballot %d", myProcess, instance, ballotNum));
			gcl.broadcastMsg(proposeMsg);
			failCheck.checkFailure(FailCheck.FailureType.AFTERSENDPROPOSE);

			// Wait for majority promises with a short timeout (150ms)
			boolean receivedPromises = false;
			try {
				receivedPromises = ctx.promiseLatch.await(150, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				if (!running) return proposedVal;
				Thread.currentThread().interrupt();
			}

			if (committedValues.containsKey(instance)) {
				activeBallots.remove(instance);
				return committedValues.get(instance);
			}

			if (!receivedPromises || ctx.preempted || ctx.promisedProcs.size() < ctx.majority) {
				logger.fine(String.format("[%s] Ballot %d preempted or timed out in Phase 1 for instance %d. Backing off...", myProcess, ballotNum, instance));
				activeBallots.remove(instance);
				backoffWait(instance);
				continue;
			}

			failCheck.checkFailure(FailCheck.FailureType.AFTERBECOMINGLEADER);
			this.logger.info(String.format("[%s] Quorum of PROMISES reached for instance %d, ballot %d", myProcess, instance, ballotNum));

			// 2. Send ACCEPT
			// If any acceptor returned a previously accepted value, safety dictates we adopt the value with the highest ballot
			Object valueToAccept = proposedVal;
			if (ctx.highestOtherAcceptedValue != null) {
				valueToAccept = ctx.highestOtherAcceptedValue;
				this.logger.info(String.format("[%s] Adopting previously accepted value from another ballot for instance %d: %s", myProcess, instance, valueToAccept));
			}

			PaxosMessage acceptMsg = new PaxosMessage("ACCEPT", instance, ballotNum, myProcess, valueToAccept, -1);
			this.logger.info(String.format("[%s] Broadcasting ACCEPT for instance %d, ballot %d with value: %s", myProcess, instance, ballotNum, valueToAccept));
			gcl.broadcastMsg(acceptMsg);

			// Wait for majority ACCEPTED acknowledgments
			boolean receivedAccepts = false;
			try {
				receivedAccepts = ctx.acceptedLatch.await(150, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				if (!running) return proposedVal;
				Thread.currentThread().interrupt();
			}

			if (committedValues.containsKey(instance)) {
				activeBallots.remove(instance);
				return committedValues.get(instance);
			}

			if (!receivedAccepts || ctx.preempted || ctx.acceptedProcs.size() < ctx.majority) {
				this.logger.fine(String.format("[%s] Ballot %d preempted or timed out in Phase 2 for instance %d. Backing off...", myProcess, ballotNum, instance));
				activeBallots.remove(instance);
				backoffWait(instance);
				continue;
			}

			failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);
			this.logger.info(String.format("[%s] Quorum of ACCEPTED acks reached for instance %d, ballot %d", myProcess, instance, ballotNum));
			activeBallots.remove(instance);

			// 3. Broadcast CONFIRM so all processes commit and deliver this instance in total order
			PaxosMessage confirmMsg = new PaxosMessage("CONFIRM", instance, ballotNum, myProcess, valueToAccept, -1);
			gcl.broadcastMsg(confirmMsg);
			recordCommit(instance, valueToAccept);

			return valueToAccept;
		}
		return proposedVal;
	}

	// Short randomized backoff that wakes up immediately if the instance is committed by another leader
	private void backoffWait(int instance) {
		long delayMs = 10 + rand.nextInt(30);
		synchronized (commitLock) {
			if (!committedValues.containsKey(instance) && running) {
				try {
					commitLock.wait(delayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	// This is what the application layer is calling to figure out what is the next message in the total order.
	// Messages delivered in ALL the processes in the group should deliver this in the same order.
	public Object acceptTOMsg() throws InterruptedException {
		return deliveredQueue.take();
	}

	public void shutdownPaxos() {
		this.logger.info(String.format("[%s] Shutting down Paxos protocol layer...", myProcess));
		running = false;
		if (listenerThread != null) {
			listenerThread.interrupt();
		}
		gcl.shutdownGCL();

		this.logger.info(String.format("[%s] Paxos layer successfully shutdown.", myProcess));
	}
}

