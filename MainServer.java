package rmi_server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import nxopen.NXException;
import nxopen.Session;
import nxopen.UFSession;

public class MainServer implements MainServerInterface {

	private Properties prop;
	private String host;
	private String serverName;
	private int numberOfProcesses;
	private int port;
	private int lowerBound;
	private int amtOfPorts;
	private long delay;
	private long period;
	private int je;
	private int runningTimeLimit;
	private HashMap<GeneralSession, Integer> runningSessions;
	private HashMap<Integer, Process> portAndProcess; // get a process from a port
	private HashMap<GeneralSession, Integer> activeSessionTimer;
	private static PortManager portManager;
	private ExecutorService pool;
	private int upperBound;
	private String processPath;
	private String NXSession;
	private String UFSession;

	static Logger logger = Logger.getLogger(MainServer.class);

	public MainServer() throws IOException {
		host = "localhost";
		serverName = "MainServer";

		pool = Executors.newFixedThreadPool(10);
		logger.setLevel(Level.DEBUG);

	}

	public HashMap<GeneralSession, Integer> getRunningSessions() {
		return this.runningSessions;
	}

	public HashMap<Integer, Process> getPortAndProcess() {
		return this.portAndProcess;
	}

	public void initSessions() {
		// Initiates a number of sessions (based on value of numberOfProcesses)
		for (int i = 0; i < numberOfProcesses; i++) {
			startSessions(port);
			++port;
		}
	}

	public void startSessions(int port) {
		// Starts a new process for each portnumber
		ProcessBuilder processBuilder = new ProcessBuilder("java.exe", "-jar", "rmi_2_ver2.jar", String.valueOf(port)).inheritIO();
		processBuilder.directory(new File(processPath));
		try {

			logger.info("Starting session with port: " + port);
			Process process = processBuilder.start();
			// Adds port and process to "starting" until they are ready
			portManager.getStartingSessions().put(port, process);
			++port;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Is called from the NXRemoteServer(session) to signal it is ready
	@Override
	public void sessionReady(int port) {
		Runnable sessionReadyThread = new SessionReadyThread(port, this);
		pool.execute(sessionReadyThread);

	}

	// General method for getting a NXSession and UFSession on the same portnumber and in the same process
	@Override
	public GeneralSession getGeneralSession() throws RemoteException, MalformedURLException, NotBoundException, NXException, InterruptedException, ExecutionException {
		// Gets the first port available
		int port = portManager.getPort(this);
		// Looks up the sessions
		Session session = (Session) Naming.lookup("//" + host + "/:" + port + "/" + NXSession);
		UFSession ufSession = (UFSession) Naming.lookup("//" + host + "/:" + port + "/" + UFSession);

		GeneralSession generalSession = new GeneralSession();
		generalSession.setSession(session);
		generalSession.setUfSession(ufSession);
		generalSession.setPort(port);
		// Adds the session to "running" and start the timer
		runningSessions.put(generalSession, port);
		activeSessionTimer.put(generalSession, 0);

		// Checks if "available" has reached lower bound, if so starts up more sessions
		if (portManager.getAvailablePorts().size() < lowerBound && portManager.getStartingSessions().isEmpty()) {
			portManager.addPorts(amtOfPorts, this);
			logger.info("Server is active");
		}

		return generalSession;
	}

	@Override
	public Session getSession(int port) throws RemoteException, MalformedURLException, NotBoundException, NXException {
		Session session = (Session) Naming.lookup("//" + host + "/:" + port + "/" + NXSession);
		GeneralSession generalSession = new GeneralSession();
		generalSession.setSession(session);
		runningSessions.put(generalSession, port);
		activeSessionTimer.put(generalSession, 0);

		return session;
	}

	@Override
	public UFSession getUFSession(int port) throws RemoteException, MalformedURLException, NotBoundException, NXException {
		UFSession ufSession = (UFSession) Naming.lookup("//" + host + "/:" + port + "/" + UFSession);
		GeneralSession generalSession = new GeneralSession();
		generalSession.setUfSession(ufSession);
		runningSessions.put(generalSession, port);
		activeSessionTimer.put(generalSession, 0);

		return ufSession;
	}

	public void initServer() throws AlreadyBoundException, IOException {
		// Gets the config. file
		InputStream input = getClass().getResourceAsStream("config.properties");
		try {
			prop = new Properties();
			// Loads the config. file
			prop.load(input);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Load the config. values
		numberOfProcesses = Integer.parseInt(prop.getProperty("numberOfProcesses"));
		port = Integer.parseInt(prop.getProperty("port"));
		host = prop.getProperty("host");
		NXSession = prop.getProperty("NXSession");
		UFSession = prop.getProperty("UFSession");
		processPath = prop.getProperty("processPath");
		serverName = prop.getProperty("serverName");
		lowerBound = Integer.parseInt(prop.getProperty("lowerBound"));
		upperBound = Integer.parseInt(prop.getProperty("upperBound"));
		amtOfPorts = Integer.parseInt(prop.getProperty("amtOfPorts"));
		delay = Long.parseLong(prop.getProperty("delay"));
		period = Long.parseLong(prop.getProperty("period"));
		runningTimeLimit = Integer.parseInt(prop.getProperty("runningTimeLimit"));

		portAndProcess = new HashMap<Integer, Process>();
		activeSessionTimer = new HashMap<GeneralSession, Integer>();
		runningSessions = new HashMap<GeneralSession, Integer>();
		portManager = PortManager.getPortManager();

		logger.info("numberOfProcess from config. file : " + numberOfProcesses);
		logger.info(processPath);
		// Setting up server

		MainServerInterface stub = (MainServerInterface) UnicastRemoteObject.exportObject(this, 0);

		// Bind the remote object's stub in the registry
		String name = "//" + host + "/" + serverName;
		LocateRegistry.createRegistry(port);
		Registry registry = LocateRegistry.getRegistry(port);
		registry.rebind(name, stub);
		++port;
		initSessions();

		// Timer that cleans up "running"
		TimerTask repeatedTask = new TimerTask() {
			@Override
			public void run() {
				logger.info("Task performed on " + new Date());
				logger.info("Length of availablePorts : " + portManager.getAvailablePorts().size());
				Iterator<GeneralSession> iterator = runningSessions.keySet().iterator();
				while (iterator.hasNext()) {
					GeneralSession key = iterator.next();
					try {
						checkSessionStatus(key, iterator);
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NXException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
		};
		Timer timer = new Timer("Timer");

		timer.scheduleAtFixedRate(repeatedTask, delay, period);

	}

	public void checkSessionStatus(GeneralSession session, Iterator<GeneralSession> iterator) throws RemoteException, NXException {
		Integer runningTime = activeSessionTimer.get(session);
		// Number of "ticks" (i.e. number of minutes) sessions are allowed to be in "running"
		if (runningTime >= runningTimeLimit) {
			int port = runningSessions.get(session);
			if (portAndProcess.containsKey(port)) {
				iterator.remove();
				activeSessionTimer.remove(session);
				Process process = portAndProcess.get(port);
				portAndProcess.remove(port);
				process.destroy();
				logger.info("Length of availableports before checkSizeAndStartSession(): " + portManager.getAvailablePorts().size());
				checkSizeAndStartSession(port);
			}

		} else {
			activeSessionTimer.put(session, ++runningTime);
		}

	}

	// Starts new sessions if a running was closed and "available" doesn't contain enough sessions.
	public void checkSizeAndStartSession(int port) {
		if (portManager.getAvailablePorts().size() < upperBound && !portManager.getStartingSessions().containsKey(port)) {
			startSessions(port);
			logger.info("Port: " + port + " was reused in checkSizeAndStartSession()");
		} else {
			logger.info("Port: " + port + " was not reused");
		}
	}

	// Invoked by the client, closes the session and checks if a new should be started.
	@Override
	public void close(GeneralSession session) {
		synchronized (this) {
			if (runningSessions.containsKey(session) && !portManager.getStartingSessions().containsKey(session.getPort()) && !portManager.getAvailablePorts().contains(session.getPort())) {
				logger.info("In Close: " + session);
				int port = session.getPort();
				Process process = portAndProcess.get(port);
				portAndProcess.remove(port);
				runningSessions.remove(session);
				process.destroy();
				checkSizeAndStartSession(port);
			}
		}
	}

	// Shuts down every process, hence closes all sessions
	@Override
	public void cleanup() {
		Iterator<Integer> iterator = portAndProcess.keySet().iterator();
		Iterator<Integer> iterator2 = portManager.getAvailablePorts().iterator();
		while (iterator.hasNext()) {
			int key = iterator.next();
			Process process = portAndProcess.get(key);
			iterator.remove();
			process.destroy();
		}
		while (iterator2.hasNext()) {
			iterator2.next();
			iterator2.remove();
		}
	}

}

class SessionReadyThread implements Runnable {
	private int port;
	private MainServer mainServer;
	static SessionReadyThread sessionReadyThread;
	private final Logger logger = Logger.getLogger(MainServer.class);

	public SessionReadyThread(int port, MainServer mainServer) {
		this.port = port;
		this.mainServer = mainServer;
	}

	@Override
	public void run() {
		synchronized (mainServer) {
			PortManager portManager = PortManager.getPortManager();
			Process process = portManager.getStartingSessions().get(port);
			portManager.getAvailablePorts().add(port);
			mainServer.getPortAndProcess().put(port, process);
			portManager.getStartingSessions().remove(port);
			logger.info("Session with portnumber: " + port + " is ready");
			logger.info("Length of availableports in sessionsReady(): " + portManager.getAvailablePorts().size());
			if (portManager.getSemaphore().availablePermits() < 1) {
				portManager.getSemaphore().release();
				logger.info("Semaphore permits i sessionsReady(): " + portManager.getSemaphore().availablePermits());
			}

		}

	}

}
