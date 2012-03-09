/*
 * Erstellt am 26.10.2011.
 */
package psimulator2;

import config.configTransformer.Loader;
import java.io.File;
import logging.Logger;
import logging.LoggingCategory;
import shared.Components.NetworkModel;
import shared.Serializer.AbstractNetworkSerializer;
import shared.Serializer.NetworkModelSerializerXML;
import shared.Serializer.SaveLoadException;
import telnetd.BootException;
import telnetd.TelnetD;
import telnetd.pridaneTridy.TelnetProperties;
import utils.Util;

/**
 *
 * @author Tomáš Pitřinec
 * @author Martin Lukáš
 */
public class Main {

	public static String configFileName;

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {

		Logger.setLogger();

		if (args.length < 1) {
			Logger.log(Logger.ERROR, LoggingCategory.XML_LOAD_SAVE,
					"No configuration file attached, run again with configuration file as first argument.");
		}

		configFileName = args[0];

		int firstTelnetPort = 11000;
		if (args.length >= 2) {
			try {
				firstTelnetPort = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				Logger.log(Logger.ERROR, LoggingCategory.XML_LOAD_SAVE, "Second argument is not port number: "+args[1]);
			}
		}

		AbstractNetworkSerializer serializer = new NetworkModelSerializerXML();	// vytvori se serializer

		NetworkModel networkModel = null;
		try {

			networkModel = serializer.loadNetworkModelFromFile(new File(configFileName));	// nacita se xmlko do ukladacich struktur

		} catch (SaveLoadException ex) {
			Logger.log(Logger.DEBUG, LoggingCategory.XML_LOAD_SAVE, Util.stackToString(ex));
			Logger.log(Logger.ERROR, LoggingCategory.XML_LOAD_SAVE, "Cannot load network model from: " + configFileName);
		}

		TelnetProperties.setStartPort(firstTelnetPort);

		Psimulator.getPsimulator().configModel = networkModel;
		Psimulator.getPsimulator().lastConfigFile = configFileName;
		Loader loader = new Loader(networkModel);	// vytvari se simulator loader
		loader.loadFromModel();	// simulator se startuje z tech ukladacich struktur


		TelnetD telnetDaemon;

		Logger.log(Logger.INFO, LoggingCategory.TELNET, "Starting telnet listeners");
		try {

			telnetDaemon = TelnetD.createTelnetD(TelnetProperties.getProperties());
			telnetDaemon.start();

			Logger.log(Logger.INFO, LoggingCategory.TELNET, "Telnet listeners successfully started");

		} catch (BootException ex) {
			Logger.log(Logger.DEBUG, LoggingCategory.TELNET, ex.toString());
			Logger.log(Logger.ERROR, LoggingCategory.TELNET, "Error occured when creating telnet servers.");
		}

	}
}