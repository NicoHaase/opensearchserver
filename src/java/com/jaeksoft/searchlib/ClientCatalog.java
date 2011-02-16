/**   
 * License Agreement for Jaeksoft OpenSearchServer
 *
 * Copyright (C) 2008-2010 Emmanuel Keller / Jaeksoft
 * 
 * http://www.open-search-server.com
 * 
 * This file is part of Jaeksoft OpenSearchServer.
 *
 * Jaeksoft OpenSearchServer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Jaeksoft OpenSearchServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Jaeksoft OpenSearchServer. 
 *  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.jaeksoft.searchlib;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.xml.sax.SAXException;
import org.zkoss.zk.ui.WebApp;

import com.jaeksoft.searchlib.config.ConfigFileRotation;
import com.jaeksoft.searchlib.config.ConfigFiles;
import com.jaeksoft.searchlib.template.TemplateAbstract;
import com.jaeksoft.searchlib.user.Role;
import com.jaeksoft.searchlib.user.User;
import com.jaeksoft.searchlib.user.UserList;
import com.jaeksoft.searchlib.util.LastModifiedAndSize;
import com.jaeksoft.searchlib.util.XPathParser;
import com.jaeksoft.searchlib.util.XmlWriter;
import com.jaeksoft.searchlib.web.controller.PushEvent;

public class ClientCatalog {

	public static final String OPENSEARCHSERVER_DATA = System
			.getenv("OPENSEARCHSERVER_DATA");

	private static volatile Map<File, Client> CLIENTS = new TreeMap<File, Client>();

	private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock(
			true);
	private static final Lock r = rwl.readLock();
	private static final Lock w = rwl.writeLock();

	private static UserList userList = null;

	private static final ConfigFiles configFiles = new ConfigFiles();

	private static final Client getClient(File indexDirectory)
			throws SearchLibException, NamingException {

		r.lock();
		try {
			Client client = CLIENTS.get(indexDirectory);
			if (client != null)
				return client;
		} finally {
			r.unlock();
		}

		w.lock();
		try {
			Client client = CLIENTS.get(indexDirectory);
			if (client != null)
				return client;
			File dataDir = new File(OPENSEARCHSERVER_DATA);
			if (!indexDirectory.getParentFile().equals(dataDir))
				throw new SearchLibException("Security alert: "
						+ indexDirectory
						+ " is outside OPENSEARCHSERVER_DATA (" + dataDir + ")");
			client = ClientFactory.INSTANCE.newClient(indexDirectory, true,
					false);
			CLIENTS.put(indexDirectory, client);
			return client;
		} finally {
			w.unlock();
		}
	}

	public static final void openAll() {
		w.lock();
		try {

			for (ClientCatalogItem catalogItem : getClientCatalog(null)) {
				Logging.logger.info("OSS load index "
						+ catalogItem.getIndexName());
				getClient(catalogItem.getIndexName());
			}
		} catch (SearchLibException e) {
			e.printStackTrace();
		} catch (NamingException e) {
			e.printStackTrace();
		} finally {
			w.unlock();
		}
	}

	public static final void closeAll() {
		w.lock();
		try {
			for (Client client : CLIENTS.values()) {
				Logging.logger
						.info("OSS unload index " + client.getIndexName());
				client.close();
			}
		} finally {
			w.unlock();
		}
	}

	public static final LastModifiedAndSize getLastModifiedAndSize(
			String indexName) throws SearchLibException {
		if (!isValidIndexName(indexName))
			throw new SearchLibException("The name '" + indexName
					+ "' is not allowed");
		File file = new File(getDataDir(), indexName);
		if (!file.exists())
			return null;
		return new LastModifiedAndSize(file);
	}

	public static final Client getClient(String indexName)
			throws SearchLibException, NamingException {
		if (!isValidIndexName(indexName))
			throw new SearchLibException("The name '" + indexName
					+ "' is not allowed");
		return getClient(new File(getDataDir(), indexName));
	}

	public static File getDataDir() throws SearchLibException {
		if (OPENSEARCHSERVER_DATA == null)
			throw new SearchLibException("OPENSEARCHSERVER_DATA is not defined");
		File dataDir = new File(OPENSEARCHSERVER_DATA);
		if (!dataDir.exists())
			throw new SearchLibException("Data directory does not exists ("
					+ dataDir + ")");
		return dataDir;
	}

	public static final Set<ClientCatalogItem> getClientCatalog(User user)
			throws SearchLibException {
		File dataDir = getDataDir();
		File[] files = dataDir
				.listFiles((FileFilter) DirectoryFileFilter.INSTANCE);
		Set<ClientCatalogItem> set = new TreeSet<ClientCatalogItem>();
		for (File file : files) {
			if (!file.isDirectory())
				continue;
			String indexName = file.getName();
			if (!isValidIndexName(indexName))
				continue;
			if (user == null || user.hasAnyRole(indexName, Role.GROUP_INDEX))
				set.add(new ClientCatalogItem(indexName));
		}
		return set;
	}

	public static final boolean exists(User user, String indexName)
			throws SearchLibException {
		if (user != null && !user.isAdmin())
			throw new SearchLibException("Operation not permitted");
		if (!isValidIndexName(indexName))
			throw new SearchLibException("The name '" + indexName
					+ "' is not allowed");
		return getClientCatalog(null)
				.contains(new ClientCatalogItem(indexName));
	}

	final private static boolean isValidIndexName(String name) {
		if (name.startsWith("."))
			return false;
		if ("logs".equals(name))
			return false;
		return true;
	}

	public static void createIndex(User user, String indexName,
			TemplateAbstract template) throws SearchLibException, IOException {
		if (user != null && !user.isAdmin())
			throw new SearchLibException("Operation not permitted");
		if (!isValidIndexName(indexName))
			throw new SearchLibException("The name '" + indexName
					+ "' is not allowed");
		w.lock();
		try {
			File indexDir = new File(getDataDir(), indexName);
			if (indexDir.exists())
				throw new SearchLibException("directory " + indexName
						+ " already exists");
			template.createIndex(indexDir);
		} finally {
			w.unlock();
		}

	}

	public static void eraseIndex(User user, String indexName)
			throws SearchLibException, NamingException, IOException {
		if (user != null && !user.isAdmin())
			throw new SearchLibException("Operation not permitted");
		if (!isValidIndexName(indexName))
			throw new SearchLibException("The name '" + indexName
					+ "' is not allowed");
		Client client = getClient(indexName);
		client.close();
		client.delete();
	}

	public static UserList getUserList() throws SearchLibException {
		r.lock();
		try {
			if (userList == null) {
				File userFile = new File(getDataDir(), "users.xml");
				if (userFile.exists()) {
					XPathParser xpp = new XPathParser(userFile);
					userList = UserList.fromXml(xpp, xpp.getNode("/users"));
				} else
					userList = new UserList();
			}
			return userList;
		} catch (ParserConfigurationException e) {
			throw new SearchLibException(e);
		} catch (SAXException e) {
			throw new SearchLibException(e);
		} catch (IOException e) {
			throw new SearchLibException(e);
		} catch (XPathExpressionException e) {
			throw new SearchLibException(e);
		} finally {
			r.unlock();
		}
	}

	public static void flushPrivileges() {
		w.lock();
		try {
			userList = null;
		} finally {
			w.unlock();
		}
	}

	private static void saveUserListWithoutLock()
			throws TransformerConfigurationException, SAXException,
			IOException, SearchLibException {
		ConfigFileRotation cfr = configFiles.get(getDataDir(), "users.xml");
		try {
			XmlWriter xmlWriter = new XmlWriter(cfr.getTempPrintWriter(),
					"UTF-8");
			getUserList().writeXml(xmlWriter);
			xmlWriter.endDocument();
			cfr.rotate();
		} finally {
			cfr.abort();
		}
	}

	public static void saveUserList() throws SearchLibException {
		w.lock();
		try {
			saveUserListWithoutLock();
		} catch (IOException e) {
			throw new SearchLibException(e);
		} catch (TransformerConfigurationException e) {
			throw new SearchLibException(e);
		} catch (SAXException e) {
			throw new SearchLibException(e);
		} finally {
			w.unlock();
		}
	}

	public static User authenticate(String login, String password)
			throws SearchLibException {
		r.lock();
		try {
			User user = getUserList().get(login);
			if (user == null)
				return null;
			if (!user.authenticate(password))
				return null;
			return user;
		} finally {
			r.unlock();
		}
	}

	public static User authenticateKey(String login, String key)
			throws SearchLibException {
		r.lock();
		try {
			User user = getUserList().get(login);
			if (user == null)
				return null;
			if (!user.authenticateKey(key))
				return null;
			return user;
		} finally {
			r.unlock();
		}
	}

	private static File getTempReceiveDir(Client client) {
		File clientDir = client.getDirectory();
		return new File(clientDir.getParentFile(), '.' + clientDir.getName());
	}

	private static File getTrashReceiveDir(Client client) {
		File clientDir = client.getDirectory();
		return new File(clientDir.getParentFile(), "._" + clientDir.getName());
	}

	public static void receive_init(Client client) throws IOException {
		File rootDir = getTempReceiveDir(client);
		FileUtils.deleteDirectory(rootDir);
		rootDir.mkdir();
	}

	public static void receive_switch(WebApp webapp, Client client)
			throws SearchLibException, NamingException, IOException {
		File trashDir = getTrashReceiveDir(client);
		File clientDir = client.getDirectory();
		if (trashDir.exists())
			FileUtils.deleteDirectory(trashDir);
		w.lock();
		try {
			client.trash(trashDir);
			getTempReceiveDir(client).renameTo(clientDir);
			CLIENTS.remove(clientDir);
			CLIENTS.put(clientDir,
					ClientFactory.INSTANCE.newClient(clientDir, true, true));
			PushEvent.CLIENT_SWITCH.publish(webapp, client);
		} finally {
			w.unlock();
		}
		client.close();
		FileUtils.deleteDirectory(trashDir);
	}

	public static void receive_dir(Client client, String filePath)
			throws IOException {
		File rootDir = getTempReceiveDir(client);
		File targetFile = new File(rootDir, filePath);
		targetFile.mkdir();
	}

	public static void receive_file(Client client, String filePath,
			InputStream is) throws IOException {
		File rootDir = getTempReceiveDir(client);
		File targetFile = new File(rootDir, filePath);
		targetFile.createNewFile();
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(targetFile);
			int len;
			byte[] buffer = new byte[131072];
			while ((len = is.read(buffer)) != -1)
				fos.write(buffer, 0, len);
		} catch (IOException e) {
			throw e;
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
