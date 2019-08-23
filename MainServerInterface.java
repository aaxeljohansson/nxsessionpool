package rmi_server;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutionException;

import nxopen.NXException;
import nxopen.Session;
import nxopen.UFSession;

public interface MainServerInterface extends Remote {

	Session getSession(int port) throws RemoteException, MalformedURLException, NotBoundException, NXException;

	UFSession getUFSession(int port) throws RemoteException, MalformedURLException, NotBoundException, NXException;

	GeneralSession getGeneralSession() throws RemoteException, MalformedURLException, NotBoundException, NXException, InterruptedException, ExecutionException;

	void close(GeneralSession session) throws Exception;

	void cleanup() throws Exception;

	void sessionReady(int port) throws Exception;
}
