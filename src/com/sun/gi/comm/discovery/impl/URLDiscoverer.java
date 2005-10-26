package com.sun.gi.comm.discovery.impl;

import com.sun.gi.comm.discovery.DiscoveredGame;
import java.net.URLConnection;
import java.net.URL;
import java.io.*;
import java.net.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.sun.gi.comm.discovery.DiscoveredLoginModule;
import com.sun.gi.comm.discovery.Discoverer;

;

public class URLDiscoverer implements Discoverer {
	URL url;

	public URLDiscoverer(URL url) {
		this.url = url;

	}

	/**
	 * games
	 * 
	 * @return DiscoveredGame[]
	 */
	public DiscoveredGame[] games() {
		URLConnection conn = null;
		try {
			try {
				conn = url.openConnection();
			} catch (IOException ex) {
				ex.printStackTrace();
				return null;
			}
			conn.connect();
			InputStream content = conn.getInputStream();
			System.out.println("Found discovery stream of size: "
					+ content.available());
			// parse XML here
			try {
				SAXParser parser = SAXParserFactory.newInstance()
						.newSAXParser();
				DiscoveryXMLHandler hdlr = new DiscoveryXMLHandler();
				parser.parse(content,hdlr);
				return hdlr.discoveredGames();
				
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();			
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	static public void main(String[] args) {
		

		try {
			URLDiscoverer disco = new URLDiscoverer(new File("FakeDiscovery.xml").toURI().toURL());
			DiscoveredGame[] games = disco.games();
			System.out.println("Discovered " + games.length + " games.");
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		}

	}
}
