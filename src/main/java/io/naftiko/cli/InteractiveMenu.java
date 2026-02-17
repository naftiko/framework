package io.naftiko.cli;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.util.List;

public class InteractiveMenu {
    
    public static String showMenu(String title, List<String> options) throws IOException {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        terminal.enterPrivateMode();
        terminal.clearScreen();
        terminal.setCursorVisible(false);
        
        int selectedIndex = 0;
        
        try {
            while (true) {
                terminal.setCursorPosition(0, 0);
                
                // Display title.
                terminal.putString(title + "\n\n");
                
                // Display options.
                for (int i = 0; i < options.size(); i++) {
                    if (i == selectedIndex) {
                        terminal.setForegroundColor(TextColor.ANSI.GREEN);
                        terminal.putString("> " + options.get(i) + " <\n");
                        terminal.setForegroundColor(TextColor.ANSI.DEFAULT);
                    } else {
                        terminal.putString("  " + options.get(i) + "  \n");
                    }
                }
                
                terminal.putString("\nUse ↑/↓ arrows to navigate, Enter to select");
                terminal.flush();
                
                // Read user selection.
                KeyStroke keyStroke = terminal.readInput();
                
                if (keyStroke.getKeyType() == KeyType.ArrowUp) {
                    selectedIndex = (selectedIndex - 1 + options.size()) % options.size();
                } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
                    selectedIndex = (selectedIndex + 1) % options.size();
                } else if (keyStroke.getKeyType() == KeyType.Enter) {
                    break;
                } else if (keyStroke.getKeyType() == KeyType.Escape) {
                    terminal.close();
                    System.exit(0);
                }
            }
            
            return options.get(selectedIndex);
            
        } finally {
            terminal.exitPrivateMode();
            terminal.close();
        }
    }
}
