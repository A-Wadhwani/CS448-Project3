package simpledb.tx.recovery;

import simpledb.file.Page;
import simpledb.log.LogMgr;
import simpledb.tx.Transaction;

/**
 * The END CHECKPOINT log record.
 * @author Edward Sciore
 */
public class EndCheckpointRecord implements LogRecord {
    public EndCheckpointRecord() {
    }

    public int op() {
        return END;
    }

    /**
     * Checkpoint records have no associated transaction,
     * and so the method returns a "dummy", negative txid.
     */
    public int txNumber() {
        return -1; // dummy value
    }

    /**
     * Does nothing, because a checkpoint record
     * contains no undo information.
     */
    public void undo(Transaction tx) {}
    public void redo(Transaction tx) {}

    public String toString() {
        return "<END>";
    }

    /**
     * A static method to write a checkpoint record to the log.
     * This log record contains the CHECKPOINT operator,
     * and nothing else.
     * @return the LSN of the last log value
     */
    public static int writeToLog(LogMgr lm) {
        byte[] rec = new byte[Integer.BYTES];
        Page p = new Page(rec);
        p.setInt(0, END);
        return lm.append(rec);
    }
}
