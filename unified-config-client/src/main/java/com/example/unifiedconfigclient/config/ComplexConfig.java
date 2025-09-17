package com.example.unifiedconfigclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "complex")
public class ComplexConfig {

    private Company company;
    private List<Module> modules;
    private ApiGateway apiGateway;

    // Getters and Setters
    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
    }

    public ApiGateway getApiGateway() {
        return apiGateway;
    }

    public void setApiGateway(ApiGateway apiGateway) {
        this.apiGateway = apiGateway;
    }

    // Nested Classes
    public static class Company {
        private String name;
        private Integer founded;
        private Headquarters headquarters;
        private List<Department> departments;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getFounded() {
            return founded;
        }

        public void setFounded(Integer founded) {
            this.founded = founded;
        }

        public Headquarters getHeadquarters() {
            return headquarters;
        }

        public void setHeadquarters(Headquarters headquarters) {
            this.headquarters = headquarters;
        }

        public List<Department> getDepartments() {
            return departments;
        }

        public void setDepartments(List<Department> departments) {
            this.departments = departments;
        }
    }

    public static class Headquarters {
        private Address address;
        private Coordinates coordinates;

        // Getters and Setters
        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public Coordinates getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(Coordinates coordinates) {
            this.coordinates = coordinates;
        }
    }

    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;

        // Getters and Setters
        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }

    public static class Coordinates {
        private Double latitude;
        private Double longitude;

        // Getters and Setters
        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }
    }

    public static class Department {
        private String name;
        private String code;
        private Double budget;
        private Manager manager;
        private List<Employee> employees;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public Double getBudget() {
            return budget;
        }

        public void setBudget(Double budget) {
            this.budget = budget;
        }

        public Manager getManager() {
            return manager;
        }

        public void setManager(Manager manager) {
            this.manager = manager;
        }

        public List<Employee> getEmployees() {
            return employees;
        }

        public void setEmployees(List<Employee> employees) {
            this.employees = employees;
        }
    }

    public static class Manager {
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private LocalDate startDate;

        // Getters and Setters
        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }
    }

    public static class Employee {
        private Integer id;
        private String firstName;
        private String lastName;
        private String email;
        private String position;
        private Double salary;
        private List<String> skills;
        private List<Project> projects;

        // Getters and Setters
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public Double getSalary() {
            return salary;
        }

        public void setSalary(Double salary) {
            this.salary = salary;
        }

        public List<String> getSkills() {
            return skills;
        }

        public void setSkills(List<String> skills) {
            this.skills = skills;
        }

        public List<Project> getProjects() {
            return projects;
        }

        public void setProjects(List<Project> projects) {
            this.projects = projects;
        }
    }

    public static class Project {
        private String name;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> technologies;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDate endDate) {
            this.endDate = endDate;
        }

        public List<String> getTechnologies() {
            return technologies;
        }

        public void setTechnologies(List<String> technologies) {
            this.technologies = technologies;
        }
    }

    public static class Module {
        private String name;
        private Boolean enabled;
        private String version;
        private List<String> dependencies;
        private Map<String, Object> configuration;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies;
        }

        public Map<String, Object> getConfiguration() {
            return configuration;
        }

        public void setConfiguration(Map<String, Object> configuration) {
            this.configuration = configuration;
        }
    }

    public static class ApiGateway {
        private List<Route> routes;

        // Getters and Setters
        public List<Route> getRoutes() {
            return routes;
        }

        public void setRoutes(List<Route> routes) {
            this.routes = routes;
        }
    }

    public static class Route {
        private String id;
        private String uri;
        private List<String> predicates;
        private List<Filter> filters;
        private Map<String, Object> metadata;

        // Getters and Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public List<String> getPredicates() {
            return predicates;
        }

        public void setPredicates(List<String> predicates) {
            this.predicates = predicates;
        }

        public List<Filter> getFilters() {
            return filters;
        }

        public void setFilters(List<Filter> filters) {
            this.filters = filters;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    public static class Filter {
        private String name;
        private Map<String, Object> args;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getArgs() {
            return args;
        }

        public void setArgs(Map<String, Object> args) {
            this.args = args;
        }
    }
}