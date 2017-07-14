package com.stonesoupprogramming

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InjectionPoint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.annotation.security.RolesAllowed
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.transaction.Transactional

@SpringBootApplication
class SpringSecurityBackendApplication

@Configuration
@EnableJpaRepositories

//The next annotation enabled @RolesAllowed annotation
@EnableGlobalMethodSecurity(jsr250Enabled = true)
//We need to extend GlobalMethodSecurityConfiguration and override the configure method
//This will allow us to secure methods
class MethodSecurityConfig : GlobalMethodSecurityConfiguration(){

    override fun configure(auth: AuthenticationManagerBuilder) {
        //In our case, we are going to use an in memory authentication
        configureAuthentication(auth)
    }

    @Bean
    fun logger(injectionPoint: InjectionPoint) : org.slf4j.Logger =
         LoggerFactory.getLogger(injectionPoint.methodParameter.containingClass)
}

@Configuration
class WebSecurityConfig : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        //My web tier is missing security on my forms!
        http.authorizeRequests().anyRequest().authenticated().and().formLogin()
    }

    override fun configure(auth: AuthenticationManagerBuilder) {
        configureAuthentication(auth)
    }
}

fun configureAuthentication(auth: AuthenticationManagerBuilder){
    auth
            .inMemoryAuthentication()
            .withUser("bob").password("bob").roles("ADMIN", "USER")
            .and()
            .withUser("gene").password("gene").roles( "USER")
}

@Entity
data class BurgerOfTheDay(
        @field: Id
        @field: GeneratedValue
        var id : Long = 0L,
        var name : String = ""
)

interface BurgerRepository : JpaRepository<BurgerOfTheDay, Long>

@Service
@Transactional
//This is our class that we are going to secure
class BurgerService(@Autowired val burgerRepository: BurgerRepository){

    @PostConstruct
    fun init(){
        val burgers = listOf(
                BurgerOfTheDay(name = "New Bacon-ings"),
                BurgerOfTheDay(name = "Last of the Mo-Jicama Burger"),
                BurgerOfTheDay(name = "Little Swiss Bunshine Burger"),
                BurgerOfTheDay(name = "Itsy Bitsy Teeny Weenie Yellow Polka-Dot Zucchini Burger"))
        burgerRepository.save(burgers)
    }

    @PreDestroy
    fun destory(){
        burgerRepository.deleteAll()
    }

    //Any user can add a new BurgerOfTheDay
    @RolesAllowed(value = *arrayOf("USER", "ADMIN"))
    fun saveBurger(burgerOfTheDay: BurgerOfTheDay) = burgerRepository.save(burgerOfTheDay)

    //But only adminstrators get to delete burgers
    @RolesAllowed(value = "ADMIN")
    fun deleteBurger(id : Long) = burgerRepository.delete(id)

    //Any user gets to see our Burgers
    @RolesAllowed(value = *arrayOf("USER", "ADMIN"))
    fun allBurgers() = burgerRepository.findAll()
}

@Controller
class IndexController(
        @Autowired val logger : Logger,
        @Autowired val burgerService: BurgerService) {

    @GetMapping("/")
    fun doGet(model : Model) : String {
        model.addAttribute("burgers", burgerService.allBurgers().toList())
        return "index"
    }

    @PostMapping("/add")
    fun saveBurger(
            @RequestParam("burgerName") burgerName : String,
            model : Model) : String {
        try {
            burgerService.saveBurger(BurgerOfTheDay(name=burgerName))
            model.addAttribute("burgers", burgerService.allBurgers().toList())
            model.addAttribute("info", "Burger has been added")
        } catch (e : Exception){
            when (e){
                is AccessDeniedException -> {
                    logger.info("Security Exception")
                }
                else -> logger.error(e.toString(), e)
            }
        } finally {
            return "index"
        }
    }

    @PostMapping("/delete")
    fun deleteBurgers(
            @RequestParam("ids") ids : LongArray,
                      model: Model) : String {

        var errorThrown = false

        ids.forEach {
            try {
                burgerService.deleteBurger(it)

                //If the user doesn't have permission to invoke a method,
                //we will get AccessDeniedException which we handle and notify the user of the error
            } catch (e : Exception){
                when (e) {
                    is AccessDeniedException -> {
                        model.addAttribute("error", "Only Bob gets to delete burgers!")
                        logger.info("Security error")
                    }
                    else -> logger.error(e.toString(), e)
                }
                errorThrown = true
            }
        }
        model.addAttribute("burgers", burgerService.allBurgers().toList())
        if(!errorThrown){
            model.addAttribute("info", "Deleted burgers")
        }
        return "index"
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(SpringSecurityBackendApplication::class.java, *args)
}
