package simpledb.tx.recovery;

import simpledb.file.Page;
import simpledb.log.LogMgr;
import simpledb.tx.Transaction;

import java.util.Vector;

/**
 * The END CHECKPOINT log record.
 * @author Edward Sciore
 */
public class EndCheckpointRecord implements LogRecord {
    Vector<Integer> activeTrans;
    public EndCheckpointRecord(Page p) {
        int tpos = Integer.BYTES;
        activeTrans = new Vector<>();
        int size = p.getInt(tpos);
        for (int i = 0; i < size; i++) {
            tpos += Integer.BYTES;
            activeTrans.add(p.getInt(tpos));
        }
    }

    public int op() {
        return ENDCHECKPOINT;
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
        return "<ENDCHECKPOINT "+ activeTrans.toString() + ">";
    }

    /**
     * A static method to write a checkpoint record to the log.
     * This log record contains the CHECKPOINT operator,
     * and nothing else.
     * @return the LSN of the last log value
     */
    public static int writeToLog(LogMgr lm, Vector<Integer> activeTrans) {
        byte[] rec = new byte[Integer.BYTES * (activeTrans.size() + 2)];
        Page p = new Page(rec);
        p.setInt(0, ENDCHECKPOINT);
        int tpos = Integer.BYTES;
        p.setInt(tpos, activeTrans.size());
        for (Integer txnum : activeTrans) {
            tpos += Integer.BYTES;
            p.setInt(tpos, txnum);
        }
        return lm.append(rec);
    }
}
