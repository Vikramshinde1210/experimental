terraform {
  backend "gcs" {
    bucket = "YOUR_TF_STATE_BUCKET"
    prefix = "terraform/network-lab/state"
  }
}