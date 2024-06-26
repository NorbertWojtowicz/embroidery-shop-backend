package com.example.embroideryshop.service;

import com.example.embroideryshop.controller.dto.ProductPaginationDto;
import com.example.embroideryshop.exception.CategoryAlreadyExistsException;
import com.example.embroideryshop.exception.CategoryInUseException;
import com.example.embroideryshop.exception.NoSuchProductException;
import com.example.embroideryshop.exception.WrongProductJsonException;
import com.example.embroideryshop.model.Category;
import com.example.embroideryshop.model.Product;
import com.example.embroideryshop.repository.CategoryRepository;
import com.example.embroideryshop.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final int PAGE_SIZE = 12;
    @Value("${path.staticResources}")
    private String resourcePath;

    public Product getJson(String product) {
        Product productJson;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            productJson = objectMapper.readValue(product, Product.class);
        } catch (IOException e) {
            throw new WrongProductJsonException();
        }
        return productJson;
    }

    @Cacheable(cacheNames = "AllProducts", key = "{#pageNumber, #sortCriteria}")
    public ProductPaginationDto getAllProducts(int pageNumber, SortCriteria sortCriteria) {
        List<Product> products = productRepository.findAllProducts(PageRequest.of(pageNumber, PAGE_SIZE,
                Sort.by(sortCriteria.getDirection(), sortCriteria.getProperty().toString()))
        );
        int totalProducts = productRepository.countProductBy();
        return createProductPaginationDto(products, pageNumber, totalProducts);
    }

    private ProductPaginationDto createProductPaginationDto(List<Product> products, int currentPage, int totalProducts) {
        int totalPages = totalProducts / PAGE_SIZE + ((totalProducts % PAGE_SIZE == 0) ? 0 : 1);
        return new ProductPaginationDto(products, totalProducts, totalPages, currentPage + 1);
    }

    @Cacheable(cacheNames = "ProductsWithName", key = "{#pageNumber, #name, #sortCriteria}")
    public ProductPaginationDto getProductsWithName(String name, int pageNumber, SortCriteria sortCriteria) {
        name = formatName(name);
        List<Product> products = productRepository.findAllByNameLikeIgnoreCase(name,
                PageRequest.of(pageNumber, PAGE_SIZE,
                        Sort.by(sortCriteria.getDirection(), sortCriteria.getProperty().toString()))
        );
        int totalProducts = productRepository.countProductByNameLikeIgnoreCase(name);
        return createProductPaginationDto(products, pageNumber, totalProducts);
    }

    private String formatName(String name) {
        return "%" + name.toLowerCase() + "%";
    }

    @CacheEvict(value = {"AllProducts", "ProductById", "ProductsWithCategory", "ProductsWithName"}, allEntries = true)
    public Product addProduct(Product product, String category, MultipartFile multipartFile) throws IOException {
        setProperProductCategory(product, category);

        String fileName = StringUtils.cleanPath(multipartFile.getOriginalFilename());
        product.setMainImageName(fileName);
        Product savedProduct = productRepository.save(product);

        String uploadDir = resourcePath  + "mainImages/" + savedProduct.getId();
        Path uploadPath = Paths.get(uploadDir);
        createDirectoriesIfNotExists(uploadPath);
        Path filePath = uploadPath.resolve(fileName);
        saveUploadedMultipartFile(multipartFile, filePath);
        return savedProduct;
    }

    private void setProperProductCategory(Product product, String category) {
        Category properCategory = categoryRepository.findByName(category.replaceAll("\"", ""));
        product.setCategory(properCategory);
    }

    private void createDirectoriesIfNotExists(Path uploadPath) throws IOException {
        if(!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
    }

    private void saveUploadedMultipartFile(MultipartFile multipartFile, Path filePath) throws IOException {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException("Nie udało się zapisać pliku: " + multipartFile.getOriginalFilename());
        }
    }

    @Cacheable(cacheNames = "ProductById", key = "#id")
    public Product getProductById(long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            throw new NoSuchProductException();
        }
        return product;
    }

    @CacheEvict(value = "AllCategories", allEntries = true)
    public Category addCategory(Category category) {
        if (categoryExists(category)) throw new CategoryAlreadyExistsException(category.getName());
        return categoryRepository.save(category);
    }

    private boolean categoryExists(Category category) {
        Category categoryFromRepo = categoryRepository.findByName(category.getName());
        return categoryFromRepo != null;
    }

    @Cacheable(cacheNames = "AllCategories")
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Cacheable(cacheNames = "ProductsWithCategory", key = "{#name, #pageNumber, #sortCriteria}")
    public ProductPaginationDto getProductsWithCategory(String name, int pageNumber, SortCriteria sortCriteria) {
        Category category = categoryRepository.findByNameIgnoreCase(name);
        if (category == null) {
            throw new NoSuchElementException();
        }
        long categoryId = category.getCategoryId();
        List<Product> products = productRepository
                .findAllByCategory_CategoryId(categoryId,
                        PageRequest.of(pageNumber, PAGE_SIZE,
                                Sort.by(sortCriteria.getDirection(), sortCriteria.getProperty().toString())));
        int totalProducts = productRepository.countProductByCategory_CategoryId(categoryId);
        return createProductPaginationDto(products, pageNumber, totalProducts);
    }

    @CacheEvict(value = {"AllProducts", "ProductById", "ProductsWithCategory", "ProductsWithName"}, allEntries = true)
    public void deleteProduct(long id) {
        productRepository.deleteById(id);
    }

    @Transactional
    @CacheEvict(value = {"AllProducts", "ProductById", "ProductsWithCategory", "ProductsWithName"}, allEntries = true)
    public Product editProduct(Product product) {
        Product productEdited = productRepository.findById(product.getId()).orElseThrow();
        productEdited.setName(product.getName());
        productEdited.setPrice(product.getPrice());
        productEdited.setMainImageName(product.getMainImageName());
        productEdited.setDescription(product.getDescription());
        setProperProductCategory(productEdited, product.getCategory().getName());
        return productEdited;
    }

    @Transactional
    @CacheEvict(value = "AllCategories", allEntries = true)
    public Category editCategory(Category category) {
        if (categoryExists(category)) {
            throw new CategoryAlreadyExistsException(category.getName());
        }
        Category categoryEdited = categoryRepository.findById(category.getCategoryId()).orElseThrow();
        categoryEdited.setName(category.getName());
        return categoryEdited;
    }

    @CacheEvict(value = "AllCategories", allEntries = true)
    public void deleteCategory(long id) {
        if (productWithCategoryExists(id)) {
            throw new CategoryInUseException();
        }
        categoryRepository.deleteById(id);
    }

    private boolean productWithCategoryExists(long categoryId) {
        List<Product> productsWithCategory = productRepository.getProductsWithCategoryId(categoryId);
        return !productsWithCategory.isEmpty();
    }
}