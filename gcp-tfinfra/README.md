# Terraform GCP Network Infrastructure

This project provisions Google Cloud infrastructure using Terraform.

The infrastructure includes:

- Auto Mode VPC Network
- Firewall Rules
- 2 Compute Engine VM Instances
- Terraform Remote Backend using GCS
- Reusable Terraform Module for VM creation

---

# Architecture

```text
                    Internet
                        |
                        |
                +----------------+
                | Firewall Rules |
                +----------------+
                        |
                +----------------+
                |   VPC Network  |
                |   mynetwork    |
                +----------------+
                    |        |
                    |        |
          +----------------+ +----------------+
          |  mynet-vm-1    | |  mynet-vm-2    |
          +----------------+ +----------------+
```

---

# Project Structure

```text
tfinfra/
│
├── backend.tf
├── provider.tf
├── variables.tf
├── terraform.tfvars
├── mynetwork.tf
├── README.md
│
└── instance/
    ├── main.tf
    └── variables.tf
```

---

# Resources Created

| Resource | Description |
|---|---|
| VPC Network | Auto mode network |
| Firewall Rule | Allows SSH, HTTP, RDP, ICMP |
| VM Instance 1 | Compute Engine VM |
| VM Instance 2 | Compute Engine VM |
| GCS Backend | Remote Terraform state storage |

---

# Prerequisites

Install:

- Terraform
- Google Cloud SDK
- GCP Project
- Billing Enabled

---

# Authenticate with GCP

```bash
gcloud auth application-default login
```

Set project:

```bash
gcloud config set project PROJECT_ID
```

---

# Create Terraform State Bucket

Create GCS bucket for remote Terraform state.

```bash
gcloud storage buckets create gs://YOUR_TF_STATE_BUCKET \
  --location=asia-south1 \
  --uniform-bucket-level-access
```

Enable bucket versioning:

```bash
gcloud storage buckets update gs://YOUR_TF_STATE_BUCKET \
  --versioning
```

---

# backend.tf

```hcl
terraform {
  backend "gcs" {
    bucket = "YOUR_TF_STATE_BUCKET"
    prefix = "terraform/network-lab/state"
  }
}
```

---

# provider.tf

```hcl
provider "google" {
  project = var.project_id
  region  = var.region
}
```

---

# variables.tf

```hcl
variable "project_id" {}

variable "region" {}

variable "zone1" {}

variable "zone2" {}
```

---

# terraform.tfvars

```hcl
project_id = "your-gcp-project-id"

region = "asia-south1"

zone1 = "asia-south1-a"

zone2 = "asia-south1-b"
```

---

# mynetwork.tf

```hcl
# Create VPC Network
resource "google_compute_network" "mynetwork" {
  name                    = "mynetwork"
  auto_create_subnetworks = true
}

# Firewall Rule
resource "google_compute_firewall" "mynetwork-allow-http-ssh-rdp-icmp" {

  name    = "mynetwork-allow-http-ssh-rdp-icmp"

  network = google_compute_network.mynetwork.self_link

  allow {
    protocol = "tcp"
    ports    = ["22", "80", "3389"]
  }

  allow {
    protocol = "icmp"
  }

  source_ranges = ["0.0.0.0/0"]
}

# VM 1
module "mynet-vm-1" {

  source = "./instance"

  instance_name = "mynet-vm-1"

  instance_zone = var.zone1

  instance_network = google_compute_network.mynetwork.self_link
}

# VM 2
module "mynet-vm-2" {

  source = "./instance"

  instance_name = "mynet-vm-2"

  instance_zone = var.zone2

  instance_network = google_compute_network.mynetwork.self_link
}
```

---

# instance/main.tf

```hcl
resource "google_compute_instance" "vm_instance" {

  name = var.instance_name

  zone = var.instance_zone

  machine_type = var.instance_type

  boot_disk {

    initialize_params {

      image = "debian-cloud/debian-11"
    }
  }

  network_interface {

    network = var.instance_network

    access_config {

    }
  }
}
```

---

# instance/variables.tf

```hcl
variable "instance_name" {}

variable "instance_zone" {}

variable "instance_network" {}

variable "instance_type" {

  default = "e2-micro"
}
```

---

# Terraform Variables

Terraform variables help make infrastructure reusable and environment independent.

---

# Difference Between variables.tf and terraform.tfvars

| File | Purpose |
|---|---|
| variables.tf | Declares variables |
| terraform.tfvars | Assigns values to variables |

---

# variables.tf

Defines:
- variable names
- types
- descriptions
- defaults

Example:

```hcl
variable "region" {
  type = string
}
```

---

# terraform.tfvars

Provides actual values.

Example:

```hcl
region = "asia-south1"
```

---

# Resource Usage Example

```hcl
machine_type = var.instance_type
```

---

# Variable Value Precedence

Terraform loads variable values in the following priority order:

| Priority | Source |
|---|---|
| 1 | CLI `-var` |
| 2 | `*.auto.tfvars` |
| 3 | `terraform.tfvars` |
| 4 | Environment Variables (`TF_VAR_*`) |
| 5 | Default values in `variables.tf` |

---

# Example

## variables.tf

```hcl
variable "region" {
  default = "us-central1"
}
```

## terraform.tfvars

```hcl
region = "asia-south1"
```

## CLI

```bash
terraform apply -var="region=europe-west1"
```

Final value used:

```text
europe-west1
```

---

# Useful Terraform Commands

---

# Initialize Terraform

```bash
terraform init
```

Initializes:
- provider plugins
- backend configuration
- modules

---

# Initialize and Upgrade Providers

```bash
terraform init -upgrade
```

Used to:
- upgrade provider versions
- upgrade modules
- refresh dependencies

---

# Initialize Without Backend

```bash
terraform init -backend=false
```

Skips backend initialization.

Useful for:
- local testing
- validation
- CI/CD dry runs

---

# Format Terraform Files

```bash
terraform fmt
```

Formats Terraform configuration files.

---

# Validate Configuration

```bash
terraform validate
```

Checks:
- syntax
- references
- configuration correctness

---

# Generate Execution Plan

```bash
terraform plan
```

Shows:
- resources to create
- modify
- destroy

without applying changes.

---

# Apply Infrastructure

```bash
terraform apply
```

Creates or updates infrastructure.

---

# Auto Approve Apply

```bash
terraform apply -auto-approve
```

Skips confirmation prompt.

---

# Destroy Infrastructure

```bash
terraform destroy
```

Deletes all managed infrastructure.

---

# Auto Approve Destroy

```bash
terraform destroy -auto-approve
```

Destroys infrastructure without confirmation.

---

# Apply Using Variable File

```bash
terraform apply -var-file="prod.tfvars"
```

Useful for:
- dev
- qa
- prod environments

---

# Pass Variables Using CLI

```bash
terraform apply -var="region=asia-south1"
```

---

# Show Terraform State Resources

```bash
terraform state list
```

---

# Show Resource Details

```bash
terraform state show RESOURCE_NAME
```

Example:

```bash
terraform state show google_compute_network.mynetwork
```

---

# Refresh State

```bash
terraform refresh
```

Updates state with actual infrastructure information.

---

# Pull Remote State

```bash
terraform state pull
```

Downloads remote state locally.

---

# Push Local State

```bash
terraform state push terraform.tfstate
```

Uploads local state to backend.

Use carefully.

---

# Terraform Workspaces

## List Workspaces

```bash
terraform workspace list
```

## Create Workspace

```bash
terraform workspace new dev
```

## Switch Workspace

```bash
terraform workspace select prod
```

---

# Enable Debug Logs

```bash
TF_LOG=DEBUG terraform apply
```

Useful for troubleshooting.

---

# Standard Terraform Workflow

```bash
terraform init

terraform fmt

terraform validate

terraform plan

terraform apply
```

---

# Terraform Backend

Terraform remote state is stored in Google Cloud Storage.

Benefits:
- shared state
- collaboration
- remote access
- versioning
- recovery support
- concurrency protection

---

# GCS State Versioning

Versioning allows:
- rollback
- state recovery
- state history

Enable using:

```bash
gcloud storage buckets update gs://YOUR_TF_STATE_BUCKET \
  --versioning
```

---

# Terraform Concurrency Protection

Terraform GCS backend uses:
- object generation numbers
- optimistic concurrency control

This prevents simultaneous state corruption.

Example:

```text
User A -> terraform apply -> success
User B -> stale generation -> apply fails safely
```

---

# Security Best Practices

- Enable bucket versioning
- Restrict IAM access
- Avoid committing tfstate files
- Use service accounts in CI/CD
- Store secrets outside Terraform code
- Use Secret Manager or Vault

---

# Connectivity Verification

SSH into VM:

```bash
gcloud compute ssh mynet-vm-1 --zone=asia-south1-a
```

Ping second VM:

```bash
ping INTERNAL_IP
```

---

# Future Improvements

- Convert to custom VPC
- Add subnetworks
- Add Managed Instance Groups
- Add Load Balancer
- Add Cloud CDN
- Add Cloud Armor
- Add startup scripts
- Add autoscaling
- Add Cloud NAT
- Use reusable Terraform registry modules
- Add CI/CD pipeline

---

# Cleanup

Destroy infrastructure:

```bash
terraform destroy
```

---

# Notes

- Auto mode VPC creates one subnet per region automatically.
- VM instances receive ephemeral external IPs.
- Firewall allows:
  - SSH (22)
  - HTTP (80)
  - RDP (3389)
  - ICMP
- VM instances are created using reusable Terraform modules.
```