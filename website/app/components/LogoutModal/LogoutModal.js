import React from 'react'

const loggingOut = (isAuthorised) => {
  sessionStorage.clear();
  isAuthorised();
}

export const LogoutModal = (props) => {
  return(
    <div className={`logout-modal-component ${props.style}`} onClick={e => props.toggleModal(e)} >
    </div>
  );
}

export const LoginModal = (props) => {
  return(
     <div className={`logout-modal-component ${props.style}`} onClick={e => props.toggleModal(e)} >
    </div>
  );
}

const Modal = (props) => {
  return(
    <div className="lm-container">
      <div className="lm-middle">
        <ModalTitle />
        <ModalContent />
        <ModalButtonGroup 
          isAuthorised={props.isAuthorised}
          toggleModal={props.toggleModal}
          />
      </div>
    </div>
  );
}

const ModalTitle = (props) => {
  return(
   <div className="lm-title">
      <span className="fa fa-sign-out"></span>
      <strong>{` LOG OUT?`}</strong>
    </div>
  );
}

const ModalContent = (props) => {
  return(
    <div className="lm-content">
      <p>Are you sure you want to log out?</p>                    
      <p>Press No if you want to continue work. Press Yes to logout current user.</p>
    </div>    
  );
}

const ModalButtonGroup = (props) => {
  return(
    <div className="lm-footer">
      <button className="btn pull-right" onClick={ e => loggingOut(props.isAuthorised) }>Yes</button>
      <button className="btn pull-right" onClick={ e => props.toggleModal(e)}>No</button>
    </div>
  );
}