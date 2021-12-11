package simpledb.tx.recovery;

import java.util.*;

import simpledb.file.*;
import simpledb.log.*;
import simpledb.buffer.*;
import simpledb.tx.Transaction;

import static simpledb.tx.recovery.LogRecord.*;

/**
 * The recovery manager.  Each transaction has its own recovery manager.
 *
 * @author Edward Sciore
 */
public class RecoveryMgr {
    private LogMgr lm;
    private BufferMgr bm;
    private Transaction tx;
    private int txnum;
    public static int undos = 0;
    public static int redos = 0;
    public static boolean DEBUG_MODE = false;

    /**
     * Create a recovery manager for the specified transaction.
     *
     * @param txnum the ID of the specified transaction
     */
    public RecoveryMgr(Transaction tx, int txnum, LogMgr lm, BufferMgr bm) {
        this.tx = tx;
        this.txnum = txnum;
        this.lm = lm;
        this.bm = bm;
        StartRecord.writeToLog(lm, txnum);
    }

    /**
     * Write a commit record to the log, and flushes it to disk.
     */
    public void commit() {
        int lsn = CommitRecord.writeToLog(lm, txnum);
        lm.flush(lsn);
    }

    /**
     * Write a rollback record to the log and flush it to disk.
     */
    public void rollback() {
        doRollback();
        bm.flushAll(txnum);
        int lsn = RollbackRecord.writeToLog(lm, txnum);
        lm.flush(lsn);
    }

    /**
     * Recover uncompleted transactions from the log
     * and then write a quiescent checkpoint record to the log and flush it.
     */
    public void recover() {
        doRecover();
        bm.flushAll();
    }

    public Vector<Integer> start() {
        Vector<Integer> active = Transaction.getActiveTransCopy();
        int lsn = StartCheckpointRecord.writeToLog(lm);
        lm.flush(lsn);
        return active;
    }

    public void end(Vector<Integer> active) {
        while (wait(active)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long time = System.nanoTime();
        for (Integer tx: active) {
            bm.flushAll(tx);
        }
        int lsn = EndCheckpointRecord.writeToLog(lm, Transaction.completedTrans);
        lm.flush(lsn);
        System.out.println("Checkpoint Time: " + (System.nanoTime() - time));
    }

    public boolean wait(Collection<Integer> active) {
        int count = 0;
        if (Transaction.activeTrans.contains(txnum))
            count++;
        for (Integer i : active)
            if (!Transaction.activeTrans.contains(i))
                count++;
        return count != active.size();
    }

    /**
     * Write a setint record to the log and return its lsn.
     *
     * @param buff   the buffer containing the page
     * @param offset the offset of the value in the page
     * @param newval the value to be written
     */
    public int setInt(Buffer buff, int offset, int newval) {
        int oldval = buff.contents().getInt(offset);
        BlockId blk = buff.block();
        return SetIntRecord.writeToLog(lm, txnum, blk, offset, oldval, newval);
    }

    /**
     * Write a setstring record to the log and return its lsn.
     *
     * @param buff   the buffer containing the page
     * @param offset the offset of the value in the page
     * @param newval the value to be written
     */
    public int setString(Buffer buff, int offset, String newval) {
        String oldval = buff.contents().getString(offset);
        BlockId blk = buff.block();
        return SetStringRecord.writeToLog(lm, txnum, blk, offset, oldval, newval);
    }

    /**
     * Rollback the transaction, by iterating
     * through the log records until it finds
     * the transaction's START record,
     * calling undo() for each of the transaction's
     * log records.
     */
    private void doRollback() {
        Iterator<byte[]> iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            if (rec.txNumber() == txnum) {
                if (rec.op() == START)
                    return;
                rec.undo(tx);
            }
        }
    }

    /**
     * Do a complete database recovery.
     * The method iterates through the log records.
     * Whenever it finds a log record for an unfinished
     * transaction, it calls undo() on that record.
     * The method stops when it encounters a CHECKPOINT record
     * or the end of the log.
     */
    private void doRecover() {
        long time = System.nanoTime();
        // t1 t2 C t3 e1 e2 EC e3
        //finding transactions after checkpoint
        ArrayList<LogRecord> logList = new ArrayList<>();
        Iterator<byte[]> iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            logList.add(rec);
            if (rec.op() == ENDCHECKPOINT) {
                // Find END checkpoint
                while (iter.hasNext()){
                    bytes = iter.next();
                    rec = LogRecord.createLogRecord(bytes);
                    logList.add(rec);
                    if (rec.op() == STARTCHECKPOINT){
                        break;
                    } // Find corresponding START checkpoint
                }
                break;
            }
        }
        //redo phase
        Collections.reverse(logList);
        Collection<Integer> undoList = new ArrayList<>();
        for (int i = 0; i < logList.size(); i++) {
            LogRecord record = logList.get(i);
            if (record.op() == START) {
                undoList.add(record.txNumber());
            } else if (record.op() == COMMIT || record.op() == ROLLBACK) {
                undoList.remove(record.txNumber());
            }
            if (DEBUG_MODE)
                System.out.println("Redo: " + record);
            record.redo(tx);
            redos++;
        }
        //undo phase
        iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            if (undoList.isEmpty()) {
                break;
            } else if (undoList.contains(rec.txNumber()) && (rec.op() == SETSTRING || rec.op() == SETINT)) {
                if (DEBUG_MODE)
                    System.out.println("Undo: " + rec);
                rec.undo(tx);
                undos++;
            } else if (undoList.contains(rec.txNumber()) && rec.op() == START) {
                undoList.remove(rec.txNumber());
                int lsn = RollbackRecord.writeToLog(lm, txnum);
                lm.flush(lsn);
            }
        }
        if (DEBUG_MODE) {
            System.out.println("Undos: " + RecoveryMgr.undos);
            System.out.println("Redos: " + RecoveryMgr.redos);
        }
        System.out.println("Recovery Time:  " + (System.nanoTime() - time));
    }

    public String getLog() {

        Iterator<byte[]> iter = lm.iterator();
        String list = "";
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            switch (rec.op()) {
                case STARTCHECKPOINT:
                    list += "START CHECKPOINT -" + rec + "\n";
                    break;
                case START:
                    list += "START TRANSACTION -" + rec + "\n";
                    break;
                case COMMIT:
                    list += "COMMIT TRANSACTION -" + rec + "\n";
                    break;
                case ROLLBACK:
                    list += "ROLLBACK TRANSACTION -" + rec + "\n";
                    break;
                case SETINT:
                    list += "SETINT -" + rec + "\n";
                    break;
                case SETSTRING:
                    list += "SETSTRING -" + rec + "\n";
                    break;
                case ENDCHECKPOINT:
                    list += "END CHECKPOINT -" + rec + "\n";
                    break;
            }
        }
        return list;
    }
}
