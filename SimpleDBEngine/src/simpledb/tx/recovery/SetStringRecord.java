package simpledb.tx.recovery;

import simpledb.file.*;
import simpledb.log.LogMgr;
import simpledb.tx.Transaction;

public class SetStringRecord implements LogRecord {
   private int txnum, offset;
   private String val, newval;
   private BlockId blk;

   /**
    * Create a new setint log record.
    * @param bb the bytebuffer containing the log values
    */
   public SetStringRecord(Page p) {
      int tpos = Integer.BYTES;
      txnum = p.getInt(tpos);
      int fpos = tpos + Integer.BYTES;
      String filename = p.getString(fpos);
      int bpos = fpos + Page.maxLength(filename.length());
      int blknum = p.getInt(bpos);
      blk = new BlockId(filename, blknum);
      int opos = bpos + Integer.BYTES;
      offset = p.getInt(opos);
      int vpos = opos + Integer.BYTES;      
      val = p.getString(vpos);
      int nvpos = vpos + Page.maxLength(val.length());
      newval = p.getString(nvpos);
   }

   public int op() {
      return SETSTRING;
   }

   public int txNumber() {
      return txnum;
   }

   public String toString() {
      return "<SETSTRING " + txnum + " " + blk + " " + offset + " " + val + " " + newval + ">";
   }

   /**
    * Replace the specified data value with the value saved in the log record.
    * The method pins a buffer to the specified block,
    * calls setInt to restore the saved value,
    * and unpins the buffer.
    * @see simpledb.tx.recovery.LogRecord#undo(int)
    */
   public void undo(Transaction tx) {
      tx.pin(blk);
      tx.setString(blk, offset, val, false); // don't log the undo!
      tx.unpin(blk);
   }

   public void redo(Transaction tx) {
      tx.pin(blk);
      tx.setString(blk, offset, newval, false); // don't log the redo!
      tx.unpin(blk);
   }

   /**
    * A static method to write a setInt record to the log.
    * This log record contains the SETINT operator,
    * followed by the transaction id, the filename, number,
    * and offset of the modified block, and the previous
    * integer value at that offset.
    * @return the LSN of the last log value
    */
   public static int writeToLog(LogMgr lm, int txnum, BlockId blk, int offset, String val, String newval) {
      int tpos = Integer.BYTES;
      int fpos = tpos + Integer.BYTES;
      int bpos = fpos + Page.maxLength(blk.fileName().length());
      int opos = bpos + Integer.BYTES;
      int vpos = opos + Integer.BYTES;
      int nvpos = vpos + Page.maxLength(val.length());
      int reclen = nvpos + Page.maxLength(newval.length());
      byte[] rec = new byte[reclen];
      Page p = new Page(rec);
      p.setInt(0, SETSTRING);
      p.setInt(tpos, txnum);
      p.setString(fpos, blk.fileName());
      p.setInt(bpos, blk.number());
      p.setInt(opos, offset);
      p.setString(vpos, val);
      p.setString(nvpos, newval);
      return lm.append(rec);
   }
}