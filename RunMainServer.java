
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;

import org.apache.log4j.Logger;

import yapp.nxcad.module.MainServer;

public class RunMainServer {
	private final static Logger logger = Logger.getLogger(MainServer.class);

	public static void main(String[] args) throws IOException {

		MainServer server = new MainServer();
		try {
			server.initServer();
		} catch (RemoteException e) {
			logger.error(e.getMessage());

		} catch (AlreadyBoundException e) {

			logger.error(e.getMessage());
		}

	}

}
