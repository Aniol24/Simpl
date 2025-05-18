package Utilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathReader {

    /**
     * Llegeix el contingut d'un fitxer i el retorna com una cadena de text.
     *
     * @param codePath el directori del fitxer a llegir
     * @return el contingut del fitxer com una cadena de text, o null si hi ha un error
     */
    public static String readFile(Path codePath) {
        try {
            return Files.readString(codePath);
        } catch (IOException e) {
            System.err.println("Could not read file: " + e.getMessage());
            return null;
        }
    }
}
