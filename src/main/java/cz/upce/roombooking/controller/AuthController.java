package cz.upce.roombooking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    // Zobrazi login stranku
    // Spring Security sam zpracuje POST /login — my jen vratime HTML template
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";  // src/main/resources/templates/auth/login.html
    }
}
