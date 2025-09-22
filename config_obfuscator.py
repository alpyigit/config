#!/usr/bin/env python3
"""
Config Obfuscator Tool

This tool obfuscates configuration keys in YAML files and updates client applications
to use the obfuscated keys.
"""

import os
import yaml
import hashlib
import re
from typing import Dict, List, Any, Tuple


class ConfigObfuscator:
    def __init__(self, project_root: str):
        self.project_root = project_root
        self.config_repo = os.path.join(project_root, "config-repo")
        self.mapping_file = os.path.join(project_root, "config-key-mapping.yaml")
        self.key_mapping = {}
        
    def generate_obfuscated_name(self, original_key: str) -> str:
        """
        Generate an obfuscated name for a configuration key.
        Uses a hash-based approach to ensure consistency.
        """
        # Create a hash of the original key
        hash_object = hashlib.md5(original_key.encode())
        hex_dig = hash_object.hexdigest()
        
        # Take first 8 characters and prefix with 'cfg_'
        obfuscated = f"cfg_{hex_dig[:8]}"
        return obfuscated
    
    def extract_keys_from_dict(self, data: Dict[str, Any], prefix: str = "") -> List[str]:
        """
        Recursively extract all keys from a nested dictionary.
        """
        keys = []
        for key, value in data.items():
            full_key = f"{prefix}.{key}" if prefix else key
            keys.append(full_key)
            
            if isinstance(value, dict):
                keys.extend(self.extract_keys_from_dict(value, full_key))
        
        return keys
    
    def obfuscate_yaml_file(self, file_path: str) -> Tuple[Dict[str, str], Dict[str, Any]]:
        """
        Obfuscate keys in a YAML file and return the mapping and updated data.
        """
        with open(file_path, 'r', encoding='utf-8') as file:
            # Load all documents (in case of multi-document YAML with ---)
            documents = list(yaml.safe_load_all(file))
        
        key_mapping = {}
        updated_documents = []
        
        for doc in documents:
            if doc is None:
                updated_documents.append(doc)
                continue
                
            # Process the document
            updated_doc, doc_mapping = self.obfuscate_document(doc)
            updated_documents.extend(updated_doc)
            key_mapping.update(doc_mapping)
        
        # Write back the obfuscated content
        with open(file_path, 'w', encoding='utf-8') as file:
            yaml.dump_all(updated_documents, file, default_flow_style=False, allow_unicode=True)
        
        return key_mapping, updated_documents[0] if updated_documents else {}
    
    def obfuscate_document(self, data: Dict[str, Any]) -> Tuple[List[Dict], Dict[str, str]]:
        """
        Obfuscate keys in a single YAML document.
        """
        if not isinstance(data, dict):
            return [data], {}
            
        key_mapping = {}
        updated_data = {}
        
        # Special handling for profile documents
        if 'spring' in data and 'profiles' in data['spring']:
            # This is a profile-specific section
            profile_data = data.copy()
            if 'configurations' in profile_data:
                obfuscated_config, config_mapping = self.obfuscate_configurations(
                    profile_data['configurations']
                )
                profile_data['configurations'] = obfuscated_config
                key_mapping.update(config_mapping)
            return [profile_data], key_mapping
        elif 'configurations' in data:
            # This is the main configuration section
            obfuscated_config, config_mapping = self.obfuscate_configurations(
                data['configurations']
            )
            data['configurations'] = obfuscated_config
            key_mapping.update(config_mapping)
            return [data], key_mapping
        else:
            # Regular document, process recursively
            for key, value in data.items():
                if isinstance(value, dict):
                    obfuscated_value, nested_mapping = self.obfuscate_document(value)
                    updated_data[key] = obfuscated_value[0] if obfuscated_value else value
                    key_mapping.update(nested_mapping)
                else:
                    updated_data[key] = value
            return [updated_data], key_mapping
    
    def obfuscate_configurations(self, config_data: Dict[str, Any]) -> Tuple[Dict[str, Any], Dict[str, str]]:
        """
        Obfuscate the configurations section.
        """
        key_mapping = {}
        obfuscated_config = {}
        
        for section_key, section_value in config_data.items():
            obfuscated_section_key = self.generate_obfuscated_name(section_key)
            # Only map original keys to obfuscated keys, not values
            key_mapping[section_key] = obfuscated_section_key
            
            if isinstance(section_value, dict):
                obfuscated_section_value, section_mapping = self.obfuscate_section(
                    section_value, f"{section_key}."
                )
                # Merge mappings, but only key mappings
                key_mapping.update(section_mapping)
                obfuscated_config[obfuscated_section_key] = obfuscated_section_value
            else:
                # Do not obfuscate values, only keys
                obfuscated_config[obfuscated_section_key] = section_value
        
        return obfuscated_config, key_mapping
    
    def obfuscate_section(self, section_data: Dict[str, Any], prefix: str) -> Tuple[Dict[str, Any], Dict[str, str]]:
        """
        Obfuscate a configuration section (like app, database, etc.).
        """
        key_mapping = {}
        obfuscated_section = {}
        
        for key, value in section_data.items():
            full_key = f"{prefix}{key}"
            obfuscated_key = self.generate_obfuscated_name(key)
            # Only map original keys to obfuscated keys, not values
            key_mapping[key] = obfuscated_key
            
            if isinstance(value, dict):
                # Handle nested dictionaries
                obfuscated_nested, nested_mapping = self.obfuscate_section(
                    value, f"{full_key}."
                )
                # Merge mappings, but only key mappings
                key_mapping.update(nested_mapping)
                obfuscated_section[obfuscated_key] = obfuscated_nested
            else:
                # Do not obfuscate values, only keys
                obfuscated_section[obfuscated_key] = value
        
        return obfuscated_section, key_mapping
    
    def process_all_config_files(self):
        """
        Process all configuration files in the config-repo directory.
        Create separate mapping files for each configuration directory found.
        """
        # First, collect all configuration files by their parent directory
        config_dirs = {}
        
        # Walk through the config-repo directory
        for root, dirs, files in os.walk(self.config_repo):
            for file in files:
                # Updated to look for files ending with runtime-config.yml
                if file.endswith(('runtime-config.yml', 'runtime-config.yaml')):
                    file_path = os.path.join(root, file)
                    # Use the parent directory name as the project identifier
                    parent_dir = os.path.basename(root)
                    
                    # If file is directly in config-repo, use file name without extension
                    if parent_dir == "config-repo":
                        parent_dir = os.path.splitext(file)[0].replace("-runtime-config", "")
                    
                    if parent_dir not in config_dirs:
                        config_dirs[parent_dir] = []
                    config_dirs[parent_dir].append(file_path)
        
        # Process each configuration directory separately
        for config_dir_name, files in config_dirs.items():
            print(f"Processing configuration directory: {config_dir_name}")
            project_mappings = {}
            
            for file_path in files:
                print(f"  Processing {file_path}")
                try:
                    mapping, _ = self.obfuscate_yaml_file(file_path)
                    project_mappings.update(mapping)
                    print(f"    Obfuscated {len(mapping)} keys")
                except Exception as e:
                    print(f"    Error processing {file_path}: {e}")
            
            # Save directory-specific mapping
            project_mapping_file = os.path.join(self.project_root, f"{config_dir_name}-key-mapping.yaml")
            self.save_mapping(project_mappings, project_mapping_file)
            print(f"  Key mapping saved to {project_mapping_file}")
        
        return config_dirs
    
    def save_mapping(self, mapping: Dict[str, str], mapping_file: str = None):
        """
        Save the key mapping to a file.
        """
        if mapping_file is None:
            mapping_file = self.mapping_file
            
        with open(mapping_file, 'w', encoding='utf-8') as file:
            yaml.dump(mapping, file, default_flow_style=False, allow_unicode=True)
        print(f"Key mapping saved to {mapping_file}")


def main():
    project_root = os.path.dirname(os.path.abspath(__file__))
    obfuscator = ConfigObfuscator(project_root)
    
    print("Starting configuration obfuscation...")
    print("Processing all configuration files...")
    obfuscator.process_all_config_files()
    print("Configuration obfuscation complete.")


if __name__ == "__main__":
    main()