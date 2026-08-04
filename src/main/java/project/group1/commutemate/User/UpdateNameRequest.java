package project.group1.commutemate.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateNameRequest {

    @NotBlank(message = "Full name cannot be blank")
    @Size(max = 100, message = "Full name is too long")
    private String fullName;

    public UpdateNameRequest() {
    }

    public UpdateNameRequest(String fullName) {
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}