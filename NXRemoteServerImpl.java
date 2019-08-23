
/*=============================================================================

                    Copyright (c) 2012 Siemens PLM Solutions
                    Unpublished - All rights reserved

===============================================================================
File description: Sample NX/Open Application
===============================================================================

=============================================================================
*/

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import org.apache.log4j.Logger;

import nxopen.BaseSession;
import nxopen.NXException;
import nxopen.NXRemotableObject;
import nxopen.Session;
import nxopen.SessionFactory;
import nxopen.UFSession;
import yapp.nxcad.module.MainServer;
import yapp.nxcad.module.MainServerInterface;

/** Implements the NXRemoteServer interface */
public class NXRemoteServerImpl extends UnicastRemoteObject implements NXRemoteServer {
	private static String host;
	private static String serverName;
	private static Session theSession;
	private static UFSession theUFSession = null;
	private boolean isShutdownAllowed;
	private NXRemoteServerImpl rmiObject = null;
	private static Boolean isDone = false;
	private static Logger logger = Logger.getLogger(MainServer.class);

	public NXRemoteServerImpl() throws RemoteException {
		super();
		host = System.getProperty("nxexamples.remoting.host");
		serverName = System.getProperty("nxexamples.remoting.servername");
		if (host == null || host.equals("")) {
			host = "localhost";
		}
		if (serverName == null) {
			serverName = "NXServer";
		}
		isShutdownAllowed = (System.getProperty("nxexamples.remoting.allowshutdown") != null);

	}

	// Setting the RMIobject
	public void setRmiObject(NXRemoteServerImpl rmiObject) {
		this.rmiObject = rmiObject;
	}

	// Getting the RMIobject
	public NXRemoteServerImpl getRmiObject() {
		return this.rmiObject;
	}

	/** Starts the server and binds it with the RMI registry */
	public void startServer(int port) throws Exception {
		System.out.println("Starting");
		NXRemotableObject.RemotingProtocol remotingProtocol = NXRemotableObject.RemotingProtocol.create(port);
		theSession = (Session) SessionFactory.get("Session", remotingProtocol);
		theUFSession = (UFSession) SessionFactory.get("UFSession", remotingProtocol);
		Naming.rebind("//" + host + "/" + ":" + port + "/" + "NXSession", theSession);
		Naming.rebind("//" + host + "/" + ":" + port + "/" + "UFSession", theUFSession);
	}

	public static void main(String[] args) throws Exception {
		// Setting the mainServers path and looks it up
		String serverName = "//" + "localhost" + "/" + "MainServer";
		Registry registry = LocateRegistry.getRegistry("localhost", 1099);
		MainServerInterface mainServer = (MainServerInterface) registry.lookup(serverName);
		try {
			// Starts  NXSRemoteServer (contains sessions) with the given portnumber
			String portInput = args[0];
			int port = Integer.valueOf(portInput);
			logger.info(port);
			NXRemoteServerImpl session = new NXRemoteServerImpl();
			LocateRegistry.createRegistry(port);
			session.startServer(port);
			// Lets mainServer know it's ready
			mainServer.sessionReady(port);
			logger.info("Session ready from RMIServer");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString());
			mainServer.sessionReady(-1);
			e.printStackTrace();
		}

	}

	public Boolean serverIsDone() {
		return isDone;
	}

	@Override
	public Session session() throws RemoteException, NXException {
		return theSession;
	}

	@Override
	public UFSession ufSession() throws RemoteException, NXException {
		return theUFSession;
	}

	@Override
	public boolean isShutdownAllowed() throws RemoteException {
		return isShutdownAllowed;
	}

	private class ShutdownThread extends Thread {
		@Override
		public void run() {
			try {
				Thread.sleep(250);
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				//If the server is run inside of NX, executing exit
				// will cause NX to shut down as well
				System.exit(0);
			}
		}
	}

	@Override
	public void shutdown() throws RemoteException {
		if (!isShutdownAllowed) {
			throw new RemoteException("Shutdown not allowed");
		}

		try {
			Naming.unbind("//" + host + "/" + serverName);
		} catch (Exception e) {
			throw new RemoteException("Exception during unbind", e);
		} finally {
			// We need to shut down the server after this method
			// has returned.  If we shut down before this method has
			// returned, the client will receive an exception.
			// So, we create a separate thread that will wait
			// briefly and then shut down the server.
			(new ShutdownThread()).start();
		}
	}

	public static int getUnloadOption() {
		return BaseSession.LibraryUnloadOption.EXPLICITLY;
	}

	public static void onUnload() {

		try {
			theSession.listingWindow().open();
			theSession.listingWindow().writeLine("UnBinding Session");
			try {
				Naming.unbind("//" + host + "/" + serverName);
				theSession.listingWindow().writeLine("Session unbound");
			} catch (Exception e) {
				theSession.listingWindow().writeLine("Exception during unbind: " + e.toString());
				throw e;
			}

		} catch (Exception e) {
			try {
				theSession.listingWindow().writeLine("Exception during onUnload: " + e.toString());
			} catch (Exception ex) {
				//Unfortunately, if we get an exception here we cannot report it
			}
		}
	}

}
