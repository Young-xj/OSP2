/* Author: Daniel Vu
 * Email Address: vud@email.sc.edu
 * Date Last Modified: September 30, 2014
 * OSP2 RR Scheduling
 *
 * With code from 
 * Introduction to Operating System Design and Implementation: 
 * The OSP 2 Approach 
 * Michael Kifer and Scott Smolka, Springer, 2007
 *
 * The code is attached with numbering that represent the corresponding step in Dr. Rose's outline.
 */

package osp.Threads;

import java.util.List;
import java.util.Vector;
import java.util.Enumeration;

import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
    private static GenericList readyQueue;

    /**
       The thread constructor. Must call 

           super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        super();
    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        readyQueue = new GenericList();
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

    The priority of the thread can be set using the getPriority/setPriority
    methods. However, OSP itself doesn't care what the actual value of
    the priority is. These methods are just provided in case priority
    scheduling is required.

    @return thread or null

        @OSPProject Threads
    */
    public static ThreadCB do_create(TaskCB task)
    {
        ThreadCB thread = null;
        if(task == null)                                    // #2
        {
            ThreadCB.dispatch();
            return null;
        }
        
        if(task.getThreadCount() >= MaxThreadsPerTask)      // #3
        {
            ThreadCB.dispatch();
            return null;
        }

        thread = new ThreadCB();                            // #3b
        thread.setPriority(task.getPriority());             // #4
        thread.setStatus(ThreadReady);                      // #5
        thread.setTask(task);                               // #6
        if(task.addThread(thread) == 0)                     // #7
        {
            ThreadCB.dispatch();
            return null;
        }
        readyQueue.append(thread);                          // #8
        ThreadCB.dispatch();                                // #9
        return thread;                                      // #10
        
    }

    /** 
    Kills the specified thread. 

    The status must be set to ThreadKill, the thread must be
    removed from the task's list of threads and its pending IORBs
    must be purged from all device queues.
        
    If some thread was on the ready queue, it must removed, if the 
    thread was running, the processor becomes idle, and dispatch() 
    must be called to resume a waiting thread.
    
    @OSPProject Threads
    */
    public void do_kill()
    {
        switch(getStatus())                                                 // #1
        {
            case ThreadReady:                                               // #2
                readyQueue.remove(this);
            break;
            case ThreadRunning:                                             // #3
                ThreadCB thread = null;
                try
                {
                    thread = MMU.getPTBR().getTask().getCurrentThread();
                    if(this == thread)
                    {
                        MMU.setPTBR(null);
                        getTask().setCurrentThread(null);					// Is this needed?
                    }
                }
                catch(NullPointerException e){}
            break;
        }
     
        
        getTask().removeThread(this);                                       // #4
        setStatus(ThreadKill);                                              // #5
        
        for(int i = 0; i<Device.getTableSize(); i++)                        // #6
        {
            Device.get(i).cancelPendingIO(this);
        }
        
        ResourceCB.giveupResources(this);                                   // #7 
        ThreadCB.dispatch();                                                // #8
        if(getTask().getThreadCount() == 0)                                 // #9
        {
            getTask().kill();
        }
        
    }

    /** Suspends the thread that is currently on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
    
    Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

    @param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
        int status = getStatus();                                       // #1
        if(status>=ThreadWaiting)                                       // #4
        {
            setStatus(getStatus()+1);
            
        }

        /*
         * Let's think about why we chose to put ThreadRunning below ThreadWaiting.
         * We note that this block of code sets the status of the thread to ThreadWaiting
         * If we were not careful about how we wrote this, the thread could go into 
         * both if statements, which could unneccesarily increment the ThreadWaiting state.
         */
        else if(status == ThreadRunning)                                // #2
        {
            ThreadCB thread = null;
            try
            {
                thread = MMU.getPTBR().getTask().getCurrentThread();
                if(this==thread)
                {
                    MMU.setPTBR(null);
                    getTask().setCurrentThread(null);
                    setStatus(ThreadWaiting);                           // #3 Check the location of this?
                }
            }
            catch(NullPointerException e){}
            

            
        }

        if(!readyQueue.contains(this))
        {
            event.addThread(this);                                      // #6
        }
        else
        {
            readyQueue.remove(this);
        }
        

        ThreadCB.dispatch();                                            // #7
    }

    /** Resumes the thread.
        
    Only a thread with the status ThreadWaiting or higher
    can be resumed.  The status must be set to ThreadReady or
    decremented, respectively.
    A ready thread should be placed on the ready queue.
    
    With code from OSP2 Handbook
    
    @OSPProject Threads
    */
    public void do_resume()
    {
        if(getStatus() < ThreadWaiting) {
            MyOut.print(this, "Attempt to resume " + this + ", which wasn't waiting");
            return;
        }
        
        // Message to indicate we are attempting to resume this thread
        MyOut.print(this, "Resuming " + this);
        
        // Set the thread's status
        if(this.getStatus() == ThreadWaiting) {
            setStatus(ThreadReady);
        } else if (this.getStatus() > ThreadWaiting) {
            setStatus(getStatus()-1);
        }
        
        // Put the thread on the ready queue, if appropriate
        if (getStatus() == ThreadReady) {
            readyQueue.append(this);
        }
        
        dispatch(); // dispatch a thread
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
    
    @return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
        ThreadCB thread = null;
        
        
        try
        {
            thread = MMU.getPTBR().getTask().getCurrentThread();    // #1
        }
        catch(NullPointerException e){}
        
        if(thread != null)                                          // #2
        {
            thread.getTask().setCurrentThread(null);
            MMU.setPTBR(null);
            thread.setStatus(ThreadReady);
            readyQueue.append(thread);
        }
        
        if(readyQueue.isEmpty())                                    // #4
        {
            MMU.setPTBR(null);
            return FAILURE;
        }
        
        else
        {
            thread = (ThreadCB) readyQueue.removeHead();            // #3
            MMU.setPTBR(thread.getTask().getPageTable());           // #5
            thread.getTask().setCurrentThread(thread);              // #6
            thread.setStatus(ThreadRunning);                        // #7

        }
        
        HTimer.set(50);                                             // #8
        return SUCCESS;                                             // #9
    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
