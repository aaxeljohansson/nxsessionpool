package rmi_server;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;

public class PortManager {

	private HashMap<Integer, Process> startingSessions;
	private Queue<Integer> availablePorts;
	private Queue<Integer> activePorts;
	private ExecutorService pool;
	private Semaphore semaphore;
	private final Logger logger = Logger.getLogger(MainServer.class);

	private static PortManager portManager = new PortManager();

	private PortManager() {
		pool = Executors.newFixedThreadPool(10);
		availablePorts = new LinkedList<>();
		activePorts = new LinkedList<>();
		startingSessions = new HashMap<Integer, Process>();
		setSemaphore(new Semaphore(1));
	}

	public static PortManager getPortManager() {

		return portManager;
	}

	// Is called when the lower bound is reached, reuses portnumbers and starts up new sessions
	public void addPorts(int numberOfPorts, MainServer mainServer) {
		for (int i = 0; i < numberOfPorts; i++) {
			int port = reusePorts(mainServer);
			mainServer.startSessions(port);
		}
	}

	// Reuses ports
	public int reusePorts(MainServer mainServer) {
		boolean portFound = false;
		int port = 1100;
		while (!portFound) {
			// 1200 is upper treshold
			if (Integer.valueOf(port).equals(1200)) {
				portFound = true;
				port = reusePorts(mainServer); // ??
			}
			if (!getAvailablePorts().contains(port) && !mainServer.getRunningSessions().containsValue(port) && !getStartingSessions().containsKey(port)) {
				portFound = true;
				return port;
			}
			++port;
		}
		return port;

	}

	// Gets a port
	public int getPort(MainServer mainServer) throws RemoteException, InterruptedException, ExecutionException {
		Callable<Integer> port = new Ports();
		Future<Integer> future = pool.submit(port);

		int returnPort = 0;

		returnPort = future.get();

		logger.info("Length of availablePorts: " + getAvailablePorts().size() + " in getPort()");

		return returnPort;
	}

	public Queue<Integer> getAvailablePorts() {
		return availablePorts;
	}

	public void setAvailablePorts(Queue<Integer> availablePorts) {
		this.availablePorts = availablePorts;
	}

	public Queue<Integer> getActivePorts() {
		return activePorts;
	}

	public void setActivePorts(Queue<Integer> activePorts) {
		this.activePorts = activePorts;
	}

	public HashMap<Integer, Process> getStartingSessions() {
		return startingSessions;
	}

	public Semaphore getSemaphore() {
		return semaphore;
	}

	public void setSemaphore(Semaphore semaphore) {
		this.semaphore = semaphore;
	}

}

class Ports implements Callable<Integer> {
	private int portNumber;
	private final Logger logger = Logger.getLogger(MainServer.class);

	@Override
	public Integer call() {
		// TODO Auto-generated method stub
		synchronized (PortManager.getPortManager()) {
			PortManager portManager = PortManager.getPortManager();
			// Acquires semaphore lock when the last port in "available" is taken, released by sessionReady() called from session
			if (portManager.getAvailablePorts().size() < 2) {
				try {
					logger.info("Semaphore available permtis: " + portManager.getSemaphore().availablePermits());
					portManager.getSemaphore().acquire();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			this.portNumber = portManager.getAvailablePorts().poll();

			logger.info("Client recieves port: " + this.portNumber);
		}
		return portNumber;
	}

}
