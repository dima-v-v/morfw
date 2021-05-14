/**
 * 
 */
package ubc.pavlab.morf.models;

/**
 * @author mjacobson
 */
public class ValidationResult {

    private final boolean success;
    private final String content;

    public ValidationResult( boolean success, String content ) {
        super();
        this.success = success;
        this.content = content;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

}
