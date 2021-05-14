package ubc.pavlab.morf.models;

import java.util.Iterator;
import java.util.Map;

public class PurgeOldJobs implements Runnable {

    private Map<String, Job> savedJobs;

    public PurgeOldJobs( Map<String, Job> savedJobs ) {
        this.savedJobs = savedJobs;
    }

    @Override
    public void run() {
        synchronized ( savedJobs ) {

            for ( Iterator<Map.Entry<String, Job>> it = savedJobs.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Job> entry = it.next();
                Job job = entry.getValue();
                if ( job.getComplete() && System.currentTimeMillis() > job.getSaveExpiredDate() ) {
                    job.purgeSaveInfo();
                    it.remove();
                }
            }
        }
    }

}
