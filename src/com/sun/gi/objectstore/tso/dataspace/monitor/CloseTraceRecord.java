package com.sun.gi.objectstore.tso.dataspace.monitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;

public class CloseTraceRecord extends TraceRecord implements Serializable
{
    private static final long serialVersionUID = 1L;

    public CloseTraceRecord(long startTime) {
        super(startTime);
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
        // OK.
        dataSpace.close();
    }

    private void readObject(ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        in.defaultReadObject();
    }
}
