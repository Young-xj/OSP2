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

import java.util.ArrayList;
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
    private static ArrayList<ThreadCB> readyQueue;
    private int lastCpuBurst,
    			estimatedBurstTime;
    private long lastDispatch;
    

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
        readyQueue = new ArrayList();
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

        
        if(task == null)                                    
        {
            ThreadCB.dispatch();
            return null;
        }
        
        if(task.getThreadCount() >= MaxThreadsPerTask)      
        {
            ThreadCB.dispatch();
            return null;
        }

        thread = new ThreadCB();   
        thread.lastCpuBurst = 10;					// #1a
        thread.estimatedBurstTime = 10;				// #1a
        thread.setPriority(task.getPriority());             
        thread.setStatus(ThreadReady);                      
        thread.setTask(task);                               
        if(task.addThread(thread) == 0)                     
        {
            ThreadCB.dispatch();
            return null;
        }
        readyQueue.add(thread);                          
        ThreadCB.dispatch();                                
        return thread;                                      
        
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
        switch(getStatus())                                                 
        {
            case ThreadReady:                                               
                readyQueue.remove(this);
            break;
            case ThreadRunning:                                             
                ThreadCB thread = null;
                try
                {
                    thread = MMU.getPTBR().getTask().getCurrentThread();
                    if(this == thread)
                    {
                        MMU.setPTBR(null);
                        getTask().setCurrentThread(null);
                        
                        
                        lastCpuBurst = (int)(HClock.get() - lastDispatch); 					// #2
																							// Check the casting of this
                        																	// Check particular location of this one
                        estimatedBurstTime = (int)((0.75*lastCpuBurst)+(0.25*estimatedBurstTime)); // #1 #3
                        if(estimatedBurstTime < 5)
                        {
                        	estimatedBurstTime = 5;
                        }
                    }
                }
                catch(NullPointerException e){}
            break;
        }
     
        
        getTask().removeThread(this);                                       
        setStatus(ThreadKill);

        
        for(int i = 0; i<Device.getTableSize(); i++)                        
        {
            Device.get(i).cancelPendingIO(this);
        }
        
        ResourceCB.giveupResources(this);                                   
        ThreadCB.dispatch();                                                
        if(getTask().getThreadCount() == 0)                                 
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
        int status = getStatus();                                       
        if(status>=ThreadWaiting)                                       
        {
            setStatus(getStatus()+1);
            
        }

        /*
         * Let's think about why we chose to put ThreadRunning below ThreadWaiting.
         * We note that this block of code sets the status of the thread to ThreadWaiting
         * If we were not careful about how we wrote this, the thread could go into 
         * both if statements, which could unneccesarily increment the ThreadWaiting state.
         */
        else if(status == ThreadRunning)                                
        {
            ThreadCB thread = null;
            try
            {
                thread = MMU.getPTBR().getTask().getCurrentThread();
                if(this==thread)
                {
                    MMU.setPTBR(null);
                    getTask().setCurrentThread(null);
                    setStatus(ThreadWaiting);
                    lastCpuBurst = (int)(HClock.get() - lastDispatch); 					// #2
                    																	// Check the casting of this
                    estimatedBurstTime = (int)((0.75*lastCpuBurst)+(0.25*estimatedBurstTime)); // #3
                    if(estimatedBurstTime < 5)
                    {
                    	estimatedBurstTime = 5;
                    }
                }
            }
            catch(NullPointerException e){}
            

            
        }

        if(!readyQueue.contains(this))
        {
            event.addThread(this);                                      
        }
        else
        {
            readyQueue.remove(this);
        }
        

        ThreadCB.dispatch();                                            
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
            readyQueue.add(this);
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
            thread = MMU.getPTBR().getTask().getCurrentThread();
        }
        catch(NullPointerException e){}
        
        if(thread != null)                                          
        {

            
            
            int timeRemaining = (int)(HClock.get() - thread.lastDispatch);
            
            int indexOfShortest = -1;
            if(timeRemaining > 2)
            {
	            for(int x = 0; x< readyQueue.size(); x++)
	            {
	            	if(readyQueue.get(x).getEstimatedBurstTime() < timeRemaining)
	            	{
	            		indexOfShortest = x;
	            	}
	            }
	            
	            if(indexOfShortest != -1)
	            {
	        		// Preempt the current thread
	        		thread.setStatus(ThreadReady);
	                thread.getTask().setCurrentThread(null);
	                MMU.setPTBR(null);
	                
	                readyQueue.get(indexOfShortest).dispatch();
	                readyQueue.get(indexOfShortest).lastDispatch = HTimer.get();
	                return SUCCESS;
	            }
	            
	            else
	            {
	            	return SUCCESS;
	            }
            }
            else
            {
            	return SUCCESS;
            }
           

            
            
            
            
            
            // In project 2 originally
            //thread.setStatus(ThreadReady);
            //readyQueue.add(thread);
            
            
            
        }
        
        if(readyQueue.isEmpty())                                    
        {
            MMU.setPTBR(null);
            return FAILURE;
        }
        
        else
        {
            thread = (ThreadCB) readyQueue.remove(0);
            MMU.setPTBR(thread.getTask().getPageTable());
            thread.getTask().setCurrentThread(thread);
            thread.setStatus(ThreadRunning);
        }
        
        
        
        
        
        thread.lastDispatch = HTimer.get(); // #2
        // # 5 Removed timer because not doing RR
        //HTimer.set(50);                                            

        return SUCCESS;
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
    public int getEstimatedBurstTime(){ return estimatedBurstTime; }


}

/*
      Feel free to add local classes to improve the readability of your code
*/
