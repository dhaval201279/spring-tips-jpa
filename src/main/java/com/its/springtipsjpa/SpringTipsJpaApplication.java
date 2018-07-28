package com.its.springtipsjpa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.SecurityContextProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryFactoryBean;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@SpringBootApplication
@EnableJpaRepositories(repositoryFactoryBeanClass = EnversRevisionRepositoryFactoryBean.class)
@EnableJpaAuditing
public class SpringTipsJpaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringTipsJpaApplication.class, args);
	}
}

@Component
@Slf4j
class JpaApplicationWriter implements ApplicationRunner {

	private final EntityManager em;
	private final TransactionTemplate transactionTemplate;
	private final CustomerRepository customerRepository;

	/**
	 * entity mgr based query execution is used for demo purpose. Ideally JpaRepository should be used
	 * */
	JpaApplicationWriter (EntityManager em, TransactionTemplate transactionTemplate, CustomerRepository customerRepository) {
		this.em = em;
		this.transactionTemplate = transactionTemplate;
		this.customerRepository = customerRepository;
	}
	@Override
	public void run(ApplicationArguments args) throws Exception {
		customerRepository.deleteAll();

		transactionTemplate.execute( status -> {
			Stream.of("Dhaval,Shah; Yatharth,Shah; Shruti,Shah".split(";"))
				.map(name -> name.split(","))
				.forEach(tpl -> this.em.persist(new Customer(tpl[0], tpl[1], new HashSet<>())));

			TypedQuery<Customer> customers = this.em.createQuery("select c from Customer c", Customer.class);

			customers.getResultList()
					.forEach(customer -> {
						log.info("Typed Query result : " + ToStringBuilder.reflectionToString(customer));
					});

			return null;
		});

		transactionTemplate.execute(x -> {
			log.info("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ count of records : " + customerRepository.findAll().size());
			customerRepository
					.findAll()
					.forEach(customer -> {
						int countOfOrders = (int) (Math.random() * 5);
						for (int i = 0; i < countOfOrders; i++) {
							customer.getOrders().add(new Order("sku_" + i));
							customerRepository.save(customer);
							log.info("Saved customer having firstName " + customer.getFirst() +
									"with sku : " + "sku_" + i);
						}
					});
			return null;
		});

		transactionTemplate.execute(x -> {
			log.info("******************************************* size : " +
					customerRepository.findByFirstAndLast("Dhaval", "Shah").size());
			customerRepository.findByFirstAndLast("Dhaval", "Shah")
					.forEach(dhaval -> {
						log.info("findByFirstAndLast : " +
								ToStringBuilder.reflectionToString(dhaval));
					});

			log.info("############################################# size : " +
					customerRepository.byFullName("Dhaval", "Shah").size());
			customerRepository.byFullName("Dhaval", "Shah")
					.forEach(dhaval -> {
						log.info("byFullName : " +
								ToStringBuilder.reflectionToString(dhaval));
					});

			log.info("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% size : " +
					customerRepository.orderSummary().size());
			customerRepository
					.orderSummary()
					.forEach(summary -> log.info(summary.getSku() + " has " +
						summary.getCount() + " instances"));
			return null;
		});

		Thread.sleep(1000 * 2);
		log.info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ size : " +
				customerRepository.byFullName("Dhaval", "Shah").size());
		customerRepository
				.byFullName("Dhaval","Shah")
				.forEach(dhavs -> {
					dhavs.setFirst("dhavs");
					customerRepository.save(dhavs);
				});

		log.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ size : " +
				customerRepository.byFullName("dhavs","Shah").size());
		customerRepository
				.byFullName("dhavs","Shah")
				.forEach(dhavs -> {
					//Long id = yathu.getId();
					customerRepository
							.findRevisions(dhavs.getId())
							.forEach(revision -> {
								log.info("Revision " + ToStringBuilder.reflectionToString(revision.getMetadata()) +
									" for entity " + ToStringBuilder.reflectionToString(revision.getEntity()));
							});
				});
	}
}

@Component
class Auditor implements AuditorAware<String> {

	/**
	 * One can use Spring SecurityContext to get the uer information to store it in creator or modifier
	 *
	 * e.g. SecurityContextProvider
	 * */
	private final String user;

	Auditor(@Value("${user.name}") String user) {
		this.user = user;
	}


	@Override
	public Optional<String> getCurrentAuditor() {
		return Optional.of(this.user);
	}
}
interface CustomerRepository extends RevisionRepository<Customer, Long, Integer>, JpaRepository<Customer, Long> {
	Collection<Customer> findByFirstAndLast( String f,  String l);

	@org.springframework.data.jpa.repository.Query ("select c from Customer c where c.first = :f and c.last = :l")
	Collection<Customer> byFullName(@Param("f") String f, @Param("l") String l);

	@org.springframework.data.jpa.repository.Query (nativeQuery = true)
	Collection<OrderSummary> orderSummary();
}

interface OrderSummary {
	long getCount();
	String getSku();
}

@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Slf4j
class MappedAuditableBase {

	@Id
	@GeneratedValue
	private Long id;

	@CreatedDate
	private LocalDateTime created;

	@LastModifiedDate
	private LocalDateTime modified;

	@CreatedBy
	private String creator;

	@LastModifiedBy
	private String modifier;

	// Likewise you can have pre and post hooks for create, update remove
	@PostRemove
	public void postRemove() {
		// Logging before removing
		log.info("Logging before removing");
	}
}

@Audited
@NamedNativeQueries(
		@NamedNativeQuery(name = "Customer.orderSummary",
			query = "select sku as sku, count(id) as count from orders group by sku")
)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "customers")
class Customer extends MappedAuditableBase {

	//@Id
	/*@javax.persistence.Id
	@GeneratedValue
	private Long id;*/

	@Column (name="first_name")
	private String first;

	@Column (name="last_name")
	private String last;

	/**
	 * If one wants to exclude composed entity from auditing use @NotAudited*/
	//@NotAudited
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "customer_fk")
	private Set<Order> orders = new HashSet<Order>();
}

@Audited
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "orders")
class Order extends  MappedAuditableBase {

	//@Id
	/*@javax.persistence.Id
	@GeneratedValue
	private Long id;*/

	private String sku;
}

@Controller
class CustomerController {
	private final CustomerRepository customerRepository;

	CustomerController(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@GetMapping("/customers.view")
	String customers(Model model) {
		model.addAttribute("customers", this.customerRepository.findAll());
		return "customers";

	}
}
