package me.Short.TheosisEconomy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter
{

    @Override
    public String format(LogRecord record)
    {
        return "[" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + " " + record.getLevel() + "] " + record.getMessage() + "\n";
    }

}