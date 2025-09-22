#!/usr/bin/env python3
"""
Config Refactor Tool

This tool updates client applications to use obfuscated configuration keys.
"""

import os
import yaml
import re
from typing import Dict, List


class ConfigRefactor:
    def __init__(self, project_root: str):
        self.project_root = project_root
        self.mapping_file = os.path.join(project_root, "config-key-mapping.yaml")
        self.key_mapping = self.load_mapping()
        
    def load_mapping(self) -> Dict[str, str]:
        """
        Load the key mapping from the mapping file.
        For project-specific refactoring, load the project-specific mapping file.
        """
        # Try to load project-specific mapping file first
        project_name = None
        if hasattr(self, 'current_project'):
            project_name = self.current_project
        else:
            # Try to determine project name from current directory
            current_dir = os.getcwd()
            for item in os.listdir(current_dir):
                if item.endswith('-key-mapping.yaml'):
                    project_name = item.replace('-key-mapping.yaml', '')
                    break
        
        if project_name:
            project_mapping_file = os.path.join(self.project_root, f"{project_name}-key-mapping.yaml")
            if os.path.exists(project_mapping_file):
                print(f"Loading project-specific mapping from {project_mapping_file}")
                with open(project_mapping_file, 'r', encoding='utf-8') as file:
                    return yaml.safe_load(file) or {}
        
        # Fallback to global mapping file
        if os.path.exists(self.mapping_file):
            print(f"Loading global mapping from {self.mapping_file}")
            with open(self.mapping_file, 'r', encoding='utf-8') as file:
                return yaml.safe_load(file) or {}
        
        print(f"Mapping file not found: {self.mapping_file}")
        return {}
    
    def find_configuration_classes(self, project_dir: str) -> List[str]:
        """
        Find all Java files with @ConfigurationProperties annotation.
        """
        config_files = []
        src_dir = os.path.join(project_dir, "src")
        
        if not os.path.exists(src_dir):
            print(f"  Warning: src directory not found in {project_dir}")
            return config_files
            
        print(f"  Searching for @ConfigurationProperties classes in {src_dir}...")
        
        # Process all Java files in the src directory recursively
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    # Check if the file contains @ConfigurationProperties annotation
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            content = f.read()
                            if '@ConfigurationProperties' in content:
                                config_files.append(file_path)
                                print(f"    Found ConfigurationProperties class: {file_path}")
                    except Exception as e:
                        print(f"  Warning: Could not read {file_path}: {e}")
        
        return config_files
    
    def update_configuration_properties(self, project_dir: str):
        """
        Update ConfigurationProperties classes in a project.
        """
        print(f"  Updating ConfigurationProperties in {project_dir}")
        
        # Find all configuration classes automatically
        config_files = self.find_configuration_classes(project_dir)
        
        if not config_files:
            print(f"  No ConfigurationProperties classes found in {project_dir}")
            return
            
        print(f"  Found {len(config_files)} configuration classes")
        
        # Process each configuration class
        for file_path in config_files:
            print(f"  Processing configuration class: {file_path}")
            self.update_java_file(file_path)
    
    def update_java_file(self, file_path: str):
        """
        Update a Java file to use obfuscated field names and method signatures.
        """
        try:
            with open(file_path, 'r', encoding='utf-8') as file:
                content = file.read()
        except Exception as e:
            print(f"  Warning: Could not read {file_path}: {e}")
            return
        
        original_content = content
        updated = False
        
        # Update field names from original to obfuscated
        for original_key, obfuscated_key in self.key_mapping.items():
            if original_key != obfuscated_key:
                # Update field declarations: private Type originalKey; -> private Type obfuscatedKey;
                field_pattern = f'(private\\s+\\w+\\s+){re.escape(original_key)}(\\s*;)'
                field_replacement = f'\\1{obfuscated_key}\\2'
                matches = re.findall(field_pattern, content)
                if matches:
                    print(f"    Found {len(matches)} field declarations to update: {original_key} -> {obfuscated_key}")
                    content = re.sub(field_pattern, field_replacement, content)
                    updated = True
                
                # Update field references in getter method implementations
                getter_ref_pattern = f'(return\\s+){re.escape(original_key)}(\\s*;)'
                getter_ref_replacement = f'\\1{obfuscated_key}\\2'
                matches = re.findall(getter_ref_pattern, content)
                if matches:
                    print(f"    Found {len(matches)} getter references to update: {original_key} -> {obfuscated_key}")
                    content = re.sub(getter_ref_pattern, getter_ref_replacement, content)
                    updated = True
                
                # Update field references in setter method implementations
                setter_ref_pattern = f'(this\\.){re.escape(original_key)}(\\s*=\\s*){re.escape(original_key)}(\\s*;)'
                setter_ref_replacement = f'\\1{obfuscated_key}\\2{obfuscated_key}\\3'
                matches = re.findall(setter_ref_pattern, content)
                if matches:
                    print(f"    Found {len(matches)} setter references to update: {original_key} -> {obfuscated_key}")
                    content = re.sub(setter_ref_pattern, setter_ref_replacement, content)
                    updated = True
                
                # Update setter method parameter names
                param_pattern = f'({original_key}\\s*\\))'
                param_replacement = f'{obfuscated_key})'
                matches = re.findall(param_pattern, content)
                if matches:
                    print(f"    Found {len(matches)} parameter names to update: {original_key} -> {obfuscated_key}")
                    content = re.sub(param_pattern, param_replacement, content)
                    updated = True
        
        # Update method signatures from original to obfuscated
        for original_key, obfuscated_key in self.key_mapping.items():
            if original_key != obfuscated_key:
                # Capitalize the first letter for method names
                original_method_key = original_key.capitalize()
                obfuscated_method_key = obfuscated_key.capitalize()
                
                # Update getter method signatures
                getter_pattern = f'(get){re.escape(original_method_key)}(\\s*\\()'
                getter_replacement = f'\\1{obfuscated_method_key}\\2'
                matches = re.findall(getter_pattern, content)
                if matches:
                    print(f"    Found {len(matches)} getter method signatures to update: get{original_method_key} -> get{obfuscated_method_key}")
                    content = re.sub(getter_pattern, getter_replacement, content)
                    updated = True
                
                # Update setter method signatures
                setter_pattern = f'(set){re.escape(original_method_key)}(\\s*\\()'
                setter_replacement = f'\\1{obfuscated_method_key}\\2'
                matches = re.findall(setter_pattern, content)
                if matches:
                    print(f"    Found {len(matches)} setter method signatures to update: set{original_method_key} -> set{obfuscated_method_key}")
                    content = re.sub(setter_pattern, setter_replacement, content)
                    updated = True
        
        if updated and content != original_content:
            try:
                with open(file_path, 'w', encoding='utf-8') as file:
                    file.write(content)
                print(f"    Updated {file_path}")
            except Exception as e:
                print(f"    Warning: Could not write to {file_path}: {e}")
        elif not updated:
            print(f"    No updates needed for {file_path}")
    
    def update_all_java_files(self, project_dir: str):
        """
        Update all Java files in src and test directories to use obfuscated method names.
        """
        # Process both src and test directories
        directories_to_process = [
            os.path.join(project_dir, "src"),
            os.path.join(project_dir, "test")
        ]
        
        for base_dir in directories_to_process:
            if os.path.exists(base_dir):
                print(f"  Scanning {base_dir} for Java files...")
                # Process all Java files in the directory recursively
                java_files_count = 0
                for root, dirs, files in os.walk(base_dir):
                    for file in files:
                        if file.endswith('.java'):
                            java_files_count += 1
                            file_path = os.path.join(root, file)
                            self.update_java_method_calls(file_path)
                print(f"  Scanned {java_files_count} Java files in {base_dir}")
            else:
                print(f"  Directory not found: {base_dir}")
    
    def update_java_method_calls(self, file_path: str):
        """
        Update a Java file to use obfuscated method calls.
        """
        try:
            with open(file_path, 'r', encoding='utf-8') as file:
                content = file.read()
        except Exception as e:
            print(f"  Warning: Could not read {file_path}: {e}")
            return
        
        original_content = content
        updated = False
        
        # Look for method calls on configuration objects
        # Pattern: configObject.getMethod() and configObject.setMethod(value)
        method_calls_updated = 0
        for original_key, obfuscated_key in self.key_mapping.items():
            if original_key != obfuscated_key:
                # Capitalize the first letter for method names
                original_method_key = original_key.capitalize()
                obfuscated_method_key = obfuscated_key.capitalize()
                
                # Update getter method calls
                # Handle: object.getMethod()
                original_getter = f'\\.get{original_method_key}\\(\\)'
                obfuscated_getter = f'.get{obfuscated_method_key}()'
                
                # Count matches before replacement
                matches = re.findall(original_getter, content)
                if matches:
                    print(f"    Found {len(matches)} getter method calls to update: get{original_method_key} -> get{obfuscated_method_key}")
                    method_calls_updated += len(matches)
                
                content = re.sub(original_getter, obfuscated_getter, content)
                
                # Also handle calls without the dot (in case it's at the beginning of a line or after whitespace)
                original_getter_no_dot = f'get{original_method_key}\\(\\)'
                obfuscated_getter_no_dot = f'get{obfuscated_method_key}()'
                
                # Only replace if it's not already preceded by a dot (to avoid double replacement)
                matches_no_dot = re.findall(f'(?<!\\.)get{original_method_key}\\(\\)', content)
                if matches_no_dot:
                    print(f"    Found {len(matches_no_dot)} getter method calls without dot to update: get{original_method_key} -> get{obfuscated_method_key}")
                    method_calls_updated += len(matches_no_dot)
                
                content = re.sub(f'(?<!\\.)get{original_method_key}\\(\\)', obfuscated_getter_no_dot, content)
                
                # Update setter method calls
                # Handle: object.setMethod(value)
                original_setter = f'\\.set{original_method_key}\\('
                obfuscated_setter = f'.set{obfuscated_method_key}('
                
                # Count matches before replacement
                matches_setter = re.findall(original_setter, content)
                if matches_setter:
                    print(f"    Found {len(matches_setter)} setter method calls to update: set{original_method_key} -> set{obfuscated_method_key}")
                    method_calls_updated += len(matches_setter)
                
                content = re.sub(original_setter, obfuscated_setter, content)
                
                # Also handle calls without the dot
                original_setter_no_dot = f'set{original_method_key}\\('
                obfuscated_setter_no_dot = f'set{obfuscated_method_key}('
                
                matches_setter_no_dot = re.findall(f'(?<!\\.)set{original_method_key}\\(', content)
                if matches_setter_no_dot:
                    print(f"    Found {len(matches_setter_no_dot)} setter method calls without dot to update: set{original_method_key} -> set{obfuscated_method_key}")
                    method_calls_updated += len(matches_setter_no_dot)
                
                content = re.sub(f'(?<!\\.)set{original_method_key}\\(', obfuscated_setter_no_dot, content)
                
                # Check if any updates were made
                if matches or matches_no_dot or matches_setter or matches_setter_no_dot:
                    updated = True
        
        if method_calls_updated > 0:
            print(f"    Updated {method_calls_updated} method calls in {file_path}")
        
        if updated and content != original_content:
            try:
                with open(file_path, 'w', encoding='utf-8') as file:
                    file.write(content)
                print(f"    Updated {file_path}")
            except Exception as e:
                print(f"    Warning: Could not write to {file_path}: {e}")
        elif not updated:
            print(f"    No method call updates needed for {file_path}")
    
    def process_all_projects(self):
        """
        Process all projects in the workspace generically.
        """
        if not self.key_mapping:
            print("No key mapping found. Run the obfuscator first.")
            return
            
        print(f"Searching for projects in {self.project_root}")
        
        # Automatically discover all directories in the project root
        # Exclude config-repo and config-server
        excluded_dirs = ["config-repo", "config-server", ".git", ".idea", "__pycache__", "node_modules", "backup"]
        projects = []
        
        print("Scanning directories...")
        for item in os.listdir(self.project_root):
            item_path = os.path.join(self.project_root, item)
            print(f"  Checking {item}...")
            
            # Check if it's a directory and not in the excluded list
            if os.path.isdir(item_path) and item not in excluded_dirs:
                print(f"    {item} is a directory and not in excluded list")
                
                # Check if it's a project by looking for src directory and pom.xml
                src_dir = os.path.join(item_path, "src")
                pom_file = os.path.join(item_path, "pom.xml")
                
                src_exists = os.path.exists(src_dir)
                pom_exists = os.path.exists(pom_file)
                
                print(f"    src directory exists: {src_exists}")
                print(f"    pom.xml exists: {pom_exists}")
                
                if src_exists and pom_exists:
                    projects.append(item)
                    print(f"    Added {item} to projects list")
                else:
                    print(f"    Skipping {item} - missing src directory or pom.xml")
            else:
                print(f"    Skipping {item} - not a directory or in excluded list")
        
        # Sort projects for consistent processing order
        projects.sort()
        
        if not projects:
            print("No Maven projects found in the workspace.")
            # Let's also check for any directories with src but without pom.xml
            print("Checking for directories with src but without pom.xml...")
            for item in os.listdir(self.project_root):
                item_path = os.path.join(self.project_root, item)
                if os.path.isdir(item_path) and item not in excluded_dirs:
                    src_dir = os.path.join(item_path, "src")
                    if os.path.exists(src_dir):
                        print(f"  Found directory with src but no pom.xml: {item}")
        
        print(f"Found {len(projects)} Maven projects: {', '.join(projects)}")
        
        for project in projects:
            project_dir = os.path.join(self.project_root, project)
            if os.path.exists(project_dir):
                print(f"Processing {project}...")
                # Set current project for mapping file selection
                self.current_project = project
                # Reload mapping for this project
                self.key_mapping = self.load_mapping()
                
                if not self.key_mapping:
                    print(f"  No key mapping found for {project}, skipping...")
                    continue
                    
                self.update_configuration_properties(project_dir)
                self.update_all_java_files(project_dir)
            else:
                print(f"Project directory not found: {project}")


def main():
    project_root = os.path.dirname(os.path.abspath(__file__))
    refactor = ConfigRefactor(project_root)
    
    print("Starting configuration refactoring...")
    
    # Process all projects generically
    print("Processing all Maven projects...")
    refactor.process_all_projects()
    
    print("Refactoring complete.")


if __name__ == "__main__":
    main()