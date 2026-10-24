package org.ecom.largecom;


import org.springframework.stereotype.Controller;

@Controller
public class HomeControler {
    public String index() {
        System.out.print("name");
        return "name";
    }
}
