package rmi_server;
import nxopen.Session;
import nxopen.UFSession;

public class GeneralSession implements java.io.Serializable {
	private Session session;
	private UFSession ufSession;
	private int port;

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public UFSession getUfSession() {
		return ufSession;
	}

	public void setUfSession(UFSession ufSession) {
		this.ufSession = ufSession;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return this.port;
	}

	public Boolean isSession(GeneralSession generalSession) {
		if (generalSession instanceof Session) {
			return true;
		}
		return false;
	}

}
