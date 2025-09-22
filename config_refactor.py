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
        """
        if not os.path.exists(self.mapping_file):
            print(f"Mapping file not found: {self.mapping_file}")
            return {}
            
        with open(self.mapping_file, 'r', encoding='utf-8') as file:
            return yaml.safe_load(file) or {}
    
    def find_configuration_classes(self, project_dir: str) -> List[str]:
        """
        Find all Java files with @ConfigurationProperties annotation.
        """
        config_files = []
        src_dir = os.path.join(project_dir, "src")
        
        if not os.path.exists(src_dir):
            return config_files
            
        # Process all Java files in the src directory recursively
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    # Check if the file contains @ConfigurationProperties annotation
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            content = f.read()
                            if '@ConfigurationProperties' in content and 'configurations' in content:
                                config_files.append(file_path)
                    except Exception as e:
                        print(f"  Warning: Could not read {file_path}: {e}")
        
        return config_files
    
    def update_configuration_properties(self, project_dir: str):
        """
        Update ConfigurationProperties classes in a project.
        """
        # Find all configuration classes automatically
        config_files = self.find_configuration_classes(project_dir)
        
        if not config_files:
            return
            
        print(f"  Found {len(config_files)} configuration classes")
        
        # Process each configuration class
        for file_path in config_files:
            self.update_java_file(file_path)
    
    def update_java_file(self, file_path: str):
        """
        Update a Java file to use obfuscated field names.
        """
        try:
            with open(file_path, 'r', encoding='utf-8') as file:
                content = file.read()
        except Exception as e:
            print(f"  Warning: Could not read {file_path}: {e}")
            return
        
        original_content = content
        updated = False
        
        # Look for field declarations and their getters/setters
        # Pattern: private Type fieldName;
        field_pattern = r'private\s+\w+\s+(\w+);'
        fields = re.findall(field_pattern, content)
        
        # Create a reverse mapping from obfuscated names to original names
        reverse_mapping = {v: k for k, v in self.key_mapping.items()}
        
        for field in fields:
            # Check if this field has an obfuscated name
            original_name = reverse_mapping.get(field)
            if original_name and original_name != field:
                # Update getter method implementation
                content = re.sub(
                    f'return\\s+{re.escape(original_name)};',
                    f'return {field};',
                    content
                )
                
                # Update setter method implementation - fix the parameter name
                content = re.sub(
                    f'this\\.{re.escape(original_name)}\\s*=\\s*{re.escape(original_name)};',
                    f'this.{field} = {field};',
                    content
                )
                
                updated = True
                print(f"  Updated field '{original_name}' to '{field}' in {os.path.basename(file_path)}")
        
        # Update method signatures
        for original_key, obfuscated_key in self.key_mapping.items():
            if original_key != obfuscated_key:
                # Capitalize the first letter for method names
                original_method_key = original_key.capitalize()
                obfuscated_method_key = obfuscated_key.capitalize()
                
                # Update getter method signature
                original_getter = f'get{original_method_key}\\(\\)'
                obfuscated_getter = f'get{obfuscated_method_key}()'
                content = re.sub(original_getter, obfuscated_getter, content)
                
                # Update setter method signature
                original_setter = f'set{original_method_key}\\('
                obfuscated_setter = f'set{obfuscated_method_key}('
                content = re.sub(original_setter, obfuscated_setter, content)
                
                # Update setter method parameter - fix the parameter name in method signature
                setter_pattern = f'set{obfuscated_method_key}\\(\\s*\\w+\\s+({original_key})\\s*\\)'
                obfuscated_setter_replacement = f'set{obfuscated_method_key}(\\1 {field})'
                content = re.sub(setter_pattern, obfuscated_setter_replacement, content)
        
        if updated and content != original_content:
            try:
                with open(file_path, 'w', encoding='utf-8') as file:
                    file.write(content)
                print(f"  Updated {file_path}")
            except Exception as e:
                print(f"  Warning: Could not write to {file_path}: {e}")
    
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
                for root, dirs, files in os.walk(base_dir):
                    for file in files:
                        if file.endswith('.java'):
                            file_path = os.path.join(root, file)
                            self.update_java_method_calls(file_path)
    
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
                    print(f"  Found {len(matches)} getter method calls to update: get{original_method_key} -> get{obfuscated_method_key}")
                
                content = re.sub(original_getter, obfuscated_getter, content)
                
                # Also handle calls without the dot (in case it's at the beginning of a line or after whitespace)
                original_getter_no_dot = f'get{original_method_key}\\(\\)'
                obfuscated_getter_no_dot = f'get{obfuscated_method_key}()'
                
                # Only replace if it's not already preceded by a dot (to avoid double replacement)
                matches_no_dot = re.findall(f'(?<!\\.)get{original_method_key}\\(\\)', content)
                if matches_no_dot:
                    print(f"  Found {len(matches_no_dot)} getter method calls without dot to update: get{original_method_key} -> get{obfuscated_method_key}")
                
                content = re.sub(f'(?<!\\.)get{original_method_key}\\(\\)', obfuscated_getter_no_dot, content)
                
                # Update setter method calls
                # Handle: object.setMethod(value)
                original_setter = f'\\.set{original_method_key}\\('
                obfuscated_setter = f'.set{obfuscated_method_key}('
                
                # Count matches before replacement
                matches_setter = re.findall(original_setter, content)
                if matches_setter:
                    print(f"  Found {len(matches_setter)} setter method calls to update: set{original_method_key} -> set{obfuscated_method_key}")
                
                content = re.sub(original_setter, obfuscated_setter, content)
                
                # Also handle calls without the dot
                original_setter_no_dot = f'set{original_method_key}\\('
                obfuscated_setter_no_dot = f'set{obfuscated_method_key}('
                
                matches_setter_no_dot = re.findall(f'(?<!\\.)set{original_method_key}\\(', content)
                if matches_setter_no_dot:
                    print(f"  Found {len(matches_setter_no_dot)} setter method calls without dot to update: set{original_method_key} -> set{obfuscated_method_key}")
                
                content = re.sub(f'(?<!\\.)set{original_method_key}\\(', obfuscated_setter_no_dot, content)
                
                # Check if any updates were made
                if matches or matches_no_dot or matches_setter or matches_setter_no_dot:
                    updated = True
        
        if updated and content != original_content:
            try:
                with open(file_path, 'w', encoding='utf-8') as file:
                    file.write(content)
                print(f"  Updated {file_path}")
            except Exception as e:
                print(f"  Warning: Could not write to {file_path}: {e}")
    
    def process_all_projects(self):
        """
        Process all Maven projects in the workspace.
        """
        if not self.key_mapping:
            print("No key mapping found. Run the obfuscator first.")
            return
            
        # Automatically discover all Maven projects in the project root
        # Exclude config-repo and config-server
        excluded_dirs = ["config-repo", "config-server", ".git", ".idea", "__pycache__", "node_modules"]
        projects = []
        
        for item in os.listdir(self.project_root):
            item_path = os.path.join(self.project_root, item)
            # Check if it's a directory and not in the excluded list
            if os.path.isdir(item_path) and item not in excluded_dirs:
                # Check if it's a Maven project by looking for pom.xml file
                pom_file = os.path.join(item_path, "pom.xml")
                if os.path.exists(pom_file):
                    projects.append(item)
        
        # Sort projects for consistent processing order
        projects.sort()
        
        if not projects:
            print("No Maven projects found in the workspace.")
            return
            
        print(f"Found {len(projects)} Maven projects: {', '.join(projects)}")
        
        for project in projects:
            project_dir = os.path.join(self.project_root, project)
            if os.path.exists(project_dir):
                print(f"Processing {project}...")
                self.update_configuration_properties(project_dir)
                self.update_all_java_files(project_dir)
            else:
                print(f"Project directory not found: {project}")


def main():
    project_root = os.path.dirname(os.path.abspath(__file__))
    refactor = ConfigRefactor(project_root)
    
    print("Starting configuration refactoring...")
    refactor.process_all_projects()
    print("Refactoring complete.")


if __name__ == "__main__":
    main()