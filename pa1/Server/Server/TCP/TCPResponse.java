package Server.TCP;

import java.io.Serializable;

/**
 * Generic serializable RPC response envelope for TCP socket communication.
 * Encapsulates the call identifier, return object value, or remote exception.
 */
public class TCPResponse implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String m_id;
	private Object m_result;
	private Exception m_exception;

	public TCPResponse()
	{
	}

	public TCPResponse(String id, Object result)
	{
		this.m_id = id;
		this.m_result = result;
		this.m_exception = null;
	}

	public TCPResponse(String id, Object result, Exception exception)
	{
		this.m_id = id;
		this.m_result = result;
		this.m_exception = exception;
	}

	public String getId()
	{
		return m_id;
	}

	public void setId(String id)
	{
		this.m_id = id;
	}

	public Object getResult()
	{
		return m_result;
	}

	public void setResult(Object result)
	{
		this.m_result = result;
	}

	public Exception getException()
	{
		return m_exception;
	}

	public void setException(Exception exception)
	{
		this.m_exception = exception;
	}

	public boolean isSuccess()
	{
		return m_exception == null;
	}

	public Object unwrap() throws Exception
	{
		if (m_exception != null)
		{
			throw m_exception;
		}
		return m_result;
	}

	@Override
	public String toString()
	{
		if (m_exception != null)
		{
			return "TCPResponse[id=" + m_id + ", error=" + m_exception.getMessage() + "]";
		}
		return "TCPResponse[id=" + m_id + ", result=" + m_result + "]";
	}
}
