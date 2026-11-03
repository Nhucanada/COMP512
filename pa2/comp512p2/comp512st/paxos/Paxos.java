package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;
import comp512.utils.FailCheck;

// Standard Java imports
import java.io.IOException;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

// ANY OTHER classes, etc., that you add must be private to this package and not visible to the application layer.

// Package-private message wrapper for Paxos protocol
class PaxosMessage implements Serializable {
	private static final long serialVersionUID = 1L;

	String type;       // "PROPOSE", "PROMISE", "DENY", "ACCEPT", "ACCEPTED", "REJECT", "COMMIT"
	int instance;
	int ballotNum;
	String sender;
	Object value;
	int acceptedBal;

	public PaxosMessage(String type, int instance, int ballotNum, String sender, Object value, int acceptedBal) {
		this.type = type;
		this.instance = instance;
		this.ballotNum = ballotNum;
		this.sender = sender;
		this.value = value;
		this.acceptedBal = acceptedBal;
	}
}

// Package-private context tracking state and responses for an active ballot attempt
class BallotContext {
	final int ballotNum;
	final int majority;

	final Set<String> promisedProcs = Collections.synchronizedSet(new HashSet<>());
	final Set<String> deniedProcs = Collections.synchronizedSet(new HashSet<>());
	volatile int highestOtherAcceptedBallot = -1;
	volatile Object highestOtherAcceptedValue = null;
	final CountDownLatch promiseLatch;

	final Set<String> acceptedProcs = Collections.synchronizedSet(new HashSet<>());
	final Set<String> rejectedProcs = Collections.synchronizedSet(new HashSet<>());
	final CountDownLatch acceptLatch;

	public BallotContext(int ballotNum, int totalProcesses) {
		this.ballotNum = ballotNum;
		this.majority = (totalProcesses / 2) + 1;
		this.promiseLatch = new CountDownLatch(majority);
		this.acceptLatch = new CountDownLatch(majority);
	}
}

// Paxos consensus module (ONLY public class in comp512st.paxos)
public class Paxos {
	private final GCL gcl;
	private final FailCheck failCheck;
	private final Logger logger;
	private final String myProcess;
	private final String[] allValidProcesses;
	private final int myProcessIndex;
	private final int majority;

	// Acceptor state per instance
	private final Map<Integer, Integer> maxPromisedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> maxAcceptedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Object> acceptedValues = new ConcurrentHashMap<>();

	// Proposer state per instance
	private final Map<Integer, BallotContext> activeBallots = new ConcurrentHashMap<>();
	private final AtomicInteger ballotSeq = new AtomicInteger(1);

	// Learner / Total Order Delivery state
	private final Map<Integer, Object> committedValues = new ConcurrentHashMap<>();
	private final BlockingQueue<Object> deliveredQueue = new LinkedBlockingQueue<>();
	private final Object deliverLock = new Object();
	private int nextDeliveryInstance = 0;

	private final Thread listenerThread;
	private volatile boolean running = true;

	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException {
		this.myProcess = myProcess;
		this.allValidProcesses = allGroupProcesses;
		this.logger = logger;
		this.failCheck = failCheck;
		this.majority = (allGroupProcesses.length / 2) + 1;

		int idx = 0;
		for (int i = 0; i < allGroupProcesses.length; i++) {
			if (allGroupProcesses[i].equals(myProcess)) {
				idx = i;
				break;
			}
		}
		this.myProcessIndex = idx;

		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger);
		this.logger.info(String.format("[PAXOS INIT] Initialized Paxos for %s (index %d, majority %d/%d)",
				myProcess, myProcessIndex, majority, allGroupProcesses.length));

		this.listenerThread = new Thread(this::listenLoop, "Paxos-Listener-" + myProcess);
		this.listenerThread.setDaemon(true);
		this.listenerThread.start();
	}

	private void listenLoop() {
		try {
			while (running) {
				GCMessage gcMsg = gcl.readGCMessage();
				if (gcMsg == null || gcMsg.val == null) {
					break;
				}
				if (gcMsg.val instanceof PaxosMessage) {
					handlePaxosMsg((PaxosMessage) gcMsg.val);
				}
			}
		} catch (InterruptedException e) {
			logger.info("[ListenLoop] Interrupted for " + myProcess);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "[ListenLoop] Error in listener loop for " + myProcess, e);
		}
	}

	private void handlePaxosMsg(PaxosMessage msg) {
		switch (msg.type) {
			case "PROPOSE": {
				failCheck.checkFailure(FailCheck.FailureType.RECEIVEPROPOSE);
				int inst = msg.instance;
				int bal = msg.ballotNum;
				int currentPromise = maxPromisedBallot.getOrDefault(inst, -1);

				if (bal > currentPromise) {
					maxPromisedBallot.put(inst, bal);
					int accBal = maxAcceptedBallot.getOrDefault(inst, -1);
					Object accVal = acceptedValues.get(inst);

					PaxosMessage promise = new PaxosMessage("PROMISE", inst, bal, myProcess, accVal, accBal);
					gcl.sendMsg(promise, msg.sender);
					failCheck.checkFailure(FailCheck.FailureType.AFTERSENDVOTE);
				} else {
					PaxosMessage deny = new PaxosMessage("DENY", inst, bal, myProcess, null, currentPromise);
					gcl.sendMsg(deny, msg.sender);
					failCheck.checkFailure(FailCheck.FailureType.AFTERSENDVOTE);
				}
				break;
			}
			case "PROMISE": {
				BallotContext ctx = activeBallots.get(msg.instance);
				if (ctx != null && ctx.ballotNum == msg.ballotNum) {
					if (ctx.promisedProcs.add(msg.sender)) {
						if (msg.acceptedBal > ctx.highestOtherAcceptedBallot && msg.value != null) {
							ctx.highestOtherAcceptedBallot = msg.acceptedBal;
							ctx.highestOtherAcceptedValue = msg.value;
						}
						ctx.promiseLatch.countDown();
					}
				}
				break;
			}
			case "DENY": {
				BallotContext ctx = activeBallots.get(msg.instance);
				if (ctx != null && ctx.ballotNum == msg.ballotNum) {
					ctx.deniedProcs.add(msg.sender);
				}
				break;
			}
			case "ACCEPT": {
				int inst = msg.instance;
				int bal = msg.ballotNum;
				int currentPromise = maxPromisedBallot.getOrDefault(inst, -1);

				if (bal >= currentPromise) {
					maxPromisedBallot.put(inst, bal);
					maxAcceptedBallot.put(inst, bal);
					acceptedValues.put(inst, msg.value);

					PaxosMessage acceptedMsg = new PaxosMessage("ACCEPTED", inst, bal, myProcess, msg.value, bal);
					gcl.sendMsg(acceptedMsg, msg.sender);
				} else {
					PaxosMessage reject = new PaxosMessage("REJECT", inst, bal, myProcess, null, currentPromise);
					gcl.sendMsg(reject, msg.sender);
				}
				break;
			}
			case "ACCEPTED": {
				BallotContext ctx = activeBallots.get(msg.instance);
				if (ctx != null && ctx.ballotNum == msg.ballotNum) {
					if (ctx.acceptedProcs.add(msg.sender)) {
						ctx.acceptLatch.countDown();
					}
				}
				break;
			}
			case "REJECT": {
				BallotContext ctx = activeBallots.get(msg.instance);
				if (ctx != null && ctx.ballotNum == msg.ballotNum) {
					ctx.rejectedProcs.add(msg.sender);
				}
				break;
			}
			case "COMMIT": {
				handleCommit(msg.instance, msg.value);
				break;
			}
			default:
				logger.warning(String.format("[%s] Unknown message type: %s", myProcess, msg.type));
				break;
		}
	}

	private void handleCommit(int instance, Object value) {
		committedValues.put(instance, value);
		drainCommitted();
	}

	private void drainCommitted() {
		synchronized (deliverLock) {
			while (committedValues.containsKey(nextDeliveryInstance)) {
				Object valToDeliver = committedValues.get(nextDeliveryInstance);
				deliveredQueue.offer(valToDeliver);
				nextDeliveryInstance++;
				deliverLock.notifyAll();
			}
		}
	}

	public void broadcastTOMsg(Object val) {
		boolean committed = false;
		while (!committed && running) {
			int inst;
			synchronized (deliverLock) {
				inst = nextDeliveryInstance;
				while (committedValues.containsKey(inst)) {
					inst++;
				}
			}

			Object chosen = runConsensus(inst, val);
			if (chosen != null && isSameMove(chosen, val)) {
				committed = true;
				break;
			}

			// If another value won instance 'inst', move is not dropped! Retry next instance.
			try {
				Thread.sleep(ThreadLocalRandom.current().nextInt(2, 8));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private Object runConsensus(int instance, Object proposedVal) {
		while (running && !committedValues.containsKey(instance)) {
			int ballotNum = ballotSeq.incrementAndGet() * 100 + myProcessIndex;
			BallotContext ctx = new BallotContext(ballotNum, allValidProcesses.length);
			activeBallots.put(instance, ctx);

			// Phase 1: PROPOSE
			PaxosMessage proposeMsg = new PaxosMessage("PROPOSE", instance, ballotNum, myProcess, null, -1);
			gcl.broadcastMsg(proposeMsg);
			failCheck.checkFailure(FailCheck.FailureType.AFTERSENDPROPOSE);

			boolean promised = false;
			try {
				promised = ctx.promiseLatch.await(60, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				if (!running) return null;
				Thread.currentThread().interrupt();
			}

			if (committedValues.containsKey(instance)) {
				activeBallots.remove(instance);
				return committedValues.get(instance);
			}

			if (!promised || ctx.deniedProcs.size() >= majority) {
				activeBallots.remove(instance);
				try {
					Thread.sleep(ThreadLocalRandom.current().nextInt(2, 10));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				continue;
			}

			failCheck.checkFailure(FailCheck.FailureType.AFTERBECOMINGLEADER);

			// Phase 2: ACCEPT
			Object valueToAccept = (ctx.highestOtherAcceptedValue != null) ? ctx.highestOtherAcceptedValue : proposedVal;
			PaxosMessage acceptMsg = new PaxosMessage("ACCEPT", instance, ballotNum, myProcess, valueToAccept, ballotNum);
			gcl.broadcastMsg(acceptMsg);

			boolean accepted = false;
			try {
				accepted = ctx.acceptLatch.await(60, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				if (!running) return null;
				Thread.currentThread().interrupt();
			}

			if (committedValues.containsKey(instance)) {
				activeBallots.remove(instance);
				return committedValues.get(instance);
			}

			if (!accepted || ctx.rejectedProcs.size() >= majority) {
				activeBallots.remove(instance);
				try {
					Thread.sleep(ThreadLocalRandom.current().nextInt(2, 10));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				continue;
			}

			// Quorum achieved!
			failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);

			// Phase 3: COMMIT broadcast
			PaxosMessage commitMsg = new PaxosMessage("COMMIT", instance, ballotNum, myProcess, valueToAccept, ballotNum);
			gcl.broadcastMsg(commitMsg);
			handleCommit(instance, valueToAccept);

			activeBallots.remove(instance);
			return valueToAccept;
		}

		return committedValues.get(instance);
	}

	private boolean isSameMove(Object a, Object b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		if (a instanceof Object[] && b instanceof Object[]) {
			return Arrays.equals((Object[]) a, (Object[]) b);
		}
		return a.equals(b);
	}

	public Object acceptTOMsg() throws InterruptedException {
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
