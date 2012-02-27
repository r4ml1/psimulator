package psimulator.userInterface;

import java.awt.Component;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import psimulator.dataLayer.DataLayerFacade;
import psimulator.dataLayer.SaveLoadException;
import psimulator.dataLayer.SimulatorEvents.SimulatorEventsWrapper;

/**
 *
 * @author Martin Švihlík <svihlma1 at fit.cvut.cz>
 */
public class SaveLoadManagerEvents extends AbstractSaveLoadManager{
    
    public SaveLoadManagerEvents(Component parentComponent, DataLayerFacade dataLayer) {
        super(parentComponent, dataLayer);
    }
    
    /**
     * Shows save dialog.
     *
     */
    public boolean doSaveAsEventsAction(SimulatorEventsWrapper simulatorEvents) {
        try {
            // save as
            return saveAsEvents(simulatorEvents);
        } catch (SaveLoadException ex) {
            showWarningSaveLoadError(ex.getParametersWrapper());
            return false;
        }
    }
    
    /**
     * Shows open dialog
     */
    public SimulatorEventsWrapper doLoadEventsAction(){
        try {
            return load();
        } catch (SaveLoadException ex) {
            showWarningSaveLoadError(ex.getParametersWrapper());
            return null;
        }
    }
    
    
    private SimulatorEventsWrapper load() throws SaveLoadException {
        int returnVal = fileChooser.showOpenDialog(parentComponent);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selctedFile = fileChooser.getSelectedFile();
            //This is where a real application would open the file.
            System.out.println("Opening file: " + selctedFile);

            // load events from file
            SimulatorEventsWrapper simulatorEvents= dataLayer.loadEventsFromFile(file);

            // set saved timestamp and file name
            setLastSavedFile(selctedFile);
            
            //
            setLastSavedTimestamp();

            return simulatorEvents;
        }
        return null;
    }
    
    /**
     * Returns true if success
     */
    private boolean saveAsEvents(SimulatorEventsWrapper simulatorEvents) throws SaveLoadException {
        int returnVal = fileChooser.showSaveDialog(parentComponent);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selctedFile = fileChooser.getSelectedFile();
            //This is where a real application would open the file.
            System.out.println("Saving as file: " + selctedFile);

            // check if overwrite
            if (selctedFile.exists()) {
                int i = showWarningPossibleOverwriteDialog(dataLayer.getString("WINDOW_TITLE"), dataLayer.getString("DO_YOU_WANT_TO_OVERWRITE"));
                
                // if OK, save dialog
                if(i == JOptionPane.OK_OPTION){
                    // save
                    saveEvents(selctedFile, simulatorEvents);
                    return true;
                }
                
                // if CANCEL, show dialog again
                if(i == JOptionPane.NO_OPTION){
                    return saveAsEvents(simulatorEvents);
                }
                
                // cancel or quit dialog
                return false;
            }else{
                // save
                saveEvents(selctedFile, simulatorEvents);
                return true;
            }
        }
        return false;
    }
    
    private void saveEvents(File file, SimulatorEventsWrapper simulatorEvents) throws SaveLoadException {
        // save events
        dataLayer.saveEventsToFile(simulatorEvents, file);

        // set saved timestamp
        setLastSavedFile(file);
        setLastSavedTimestamp();
    }
    
    
}